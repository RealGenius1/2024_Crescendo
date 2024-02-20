// Copyright (c) 2024 FRC 167
// https://www.thebluealliance.com/team/167
// https://github.com/icrobotics-team167
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// version 3 as published by the Free Software Foundation or
// available in the root directory of this project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

package frc.robot.subsystems.swerve.interfaceLayers;

import static edu.wpi.first.units.Units.*;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.signals.AbsoluteSensorRangeValue;
import com.revrobotics.CANSparkBase.ControlType;
import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.CANSparkLowLevel.PeriodicFrame;
import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkPIDController;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.units.*;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.subsystems.swerve.Module;
import frc.robot.subsystems.swerve.SwerveSubsystem;
import frc.robot.util.CANConstants;
import frc.robot.util.CANConstants.Drivebase;
import frc.robot.util.SwerveUtils;
import frc.robot.util.motorUtils.SparkUtils;
import java.util.Queue;
import java.util.Set;

/**
 * Module IO implementation for a Spark Max drive motor controller, Spark Max azimuth motor
 * controller (NEO or NEO 550), and an analog absolute encoder connected to the RIO
 *
 * <p>NOTE: This implementation should be used as a starting point and adapted to different hardware
 * configurations.
 *
 * <p>To calibrate the absolute encoder offsets, point the modules straight (such that forward
 * motion on the drive motor will propel the robot forward) and copy the reported values from the
 * absolute encoders using AdvantageScope.
 */
public class ModuleIOSparkMax implements ModuleIO {
  /** The Spark Max motor controller for the drive motor. */
  private final CANSparkMax driveSparkMax;
  /** The Spark Max motor controller for the azimuth motor. */
  private final CANSparkMax azimuthSparkMax;

  /** The internal encoder of the drive motor. */
  private final RelativeEncoder driveEncoder;
  /** The internal encoder of the azimuth motor. */
  private final RelativeEncoder azimuthRelativeEncoder;
  /** The PID controller for the drive motor. */
  private final SparkPIDController drivePIDController;
  /** The FF constants of the drive motor. */
  private final SimpleMotorFeedforward driveFF;
  /** The PID controller for the azimuth motor. */
  private final PIDController azimuthPIDController;
  /** The absolute encoder for azimuth. */
  private final CANcoder azimuthCANcoder;
  /**
   * A {@link Queue} holding all the timestamps that the async odometry thread captures.
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Seconds
   *       </ul>
   * </ul>
   */
  private final Queue<Double> timestampQueue;
  /**
   * A {@link Queue} holding all the drive positions that the async odometry thread captures.
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Meters
   *       </ul>
   * </ul>
   */
  private final Queue<Double> drivePositionQueue;
  /**
   * A {@link Queue} holding all the azimuth positions that the async odometry thread captures.
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Rotations
   *       </ul>
   * </ul>
   */
  private final Queue<Double> azimuthPositionQueue;
  /**
   * The absolute position of the module azimuth, as measured by an absolute encoder. 0 should mean
   * the module is facing forwards. Wraps [-0.5, 0.5)
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Rotations
   *       </ul>
   * </ul>
   */
  private final StatusSignal<Double> azimuthAbsolutePosition;
  /**
   * The velocity of the module azimuth. CCW+.
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Rotations per second
   *       </ul>
   * </ul>
   */
  private final StatusSignal<Double> azimuthVelocity;
  /**
   * Due to the nature of mounting magnets for absolute encoders, it is practically impossible to
   * line up magnetic north with forwards on the module. This value is added to the raw detected
   * position, such that 0 is actually forwards on the azimuth.
   *
   * <ul>
   *   <li><b>Units:</b>
   *       <ul>
   *         <li>Rotations
   *       </ul>
   * </ul>
   */
  private final double absoluteEncoderOffset;

  /**
   * Constructs a new Spark Max-based swerve module IO interface.
   *
   * @param index The index of the module.
   *     <ul>
   *       <li><b>Position of module from index</b>
   *           <ul>
   *             <li>0: Front Left
   *             <li>1: Front Right
   *             <li>2: Back Left
   *             <li>3: Back Right
   *           </ul>
   *     </ul>
   */
  public ModuleIOSparkMax(int index) {
    // PIDF tuning values for this module. NONE OF THESE VALUES SHOULD BE NEGATIVE, IF THEY ARE
    // YA DONE GOOFED SOMEWHERE
    double drive_kS; // Volts of additional voltage needed to overcome friction
    double drive_kV; // Volts of additional voltage per m/s of velocity setpoint
    double drive_kP; // % Output per m/s of error
    // kI is typically unnecesary for driving as there's no significant factors that can prevent
    // a PID controller from hitting its target, such as gravity for an arm. Factors like friction
    // and inertia can be accounted for using kS and kV.
    double drive_kD; // % Output per m/s^2 of error derivative
    double azimuth_kP; // % Output per rotation of error
    double azimuth_KD; // % Output per rotations/s of error derivative
    switch (index) {
      case 0: // Front Left
        driveSparkMax = new CANSparkMax(Drivebase.FRONT_LEFT_DRIVE, MotorType.kBrushless);
        azimuthSparkMax = new CANSparkMax(Drivebase.FRONT_LEFT_TURN, MotorType.kBrushless);
        azimuthCANcoder = new CANcoder(Drivebase.FRONT_LEFT_ENCODER, CANConstants.CANIVORE_NAME);
        absoluteEncoderOffset = -0.270263671875;

        drive_kS = 0;
        drive_kV = 12 / SwerveSubsystem.MAX_LINEAR_SPEED.in(MetersPerSecond);
        drive_kP = 1;
        drive_kD = 0;

        azimuth_kP = 0;
        azimuth_KD = 0.0;
        break;
      case 1: // Front Right
        driveSparkMax = new CANSparkMax(Drivebase.FRONT_RIGHT_DRIVE, MotorType.kBrushless);
        azimuthSparkMax = new CANSparkMax(Drivebase.FRONT_RIGHT_TURN, MotorType.kBrushless);
        azimuthCANcoder = new CANcoder(Drivebase.FRONT_RIGHT_ENCODER, CANConstants.CANIVORE_NAME);
        absoluteEncoderOffset = -0.106689453125;

        drive_kS = 0;
        drive_kV = 12 / SwerveSubsystem.MAX_LINEAR_SPEED.in(MetersPerSecond);
        drive_kP = 1;
        drive_kD = 0;

        azimuth_kP = 0;
        azimuth_KD = 0.0;
        break;
      case 2: // Back Left
        driveSparkMax = new CANSparkMax(Drivebase.BACK_LEFT_DRIVE, MotorType.kBrushless);
        azimuthSparkMax = new CANSparkMax(Drivebase.BACK_LEFT_TURN, MotorType.kBrushless);
        azimuthCANcoder = new CANcoder(Drivebase.BACK_LEFT_ENCODER, CANConstants.CANIVORE_NAME);
        absoluteEncoderOffset = -0.1962890625;

        drive_kS = 0;
        drive_kV = 12 / SwerveSubsystem.MAX_LINEAR_SPEED.in(MetersPerSecond);
        drive_kP = 1;
        drive_kD = 0;

        azimuth_kP = 0;
        azimuth_KD = 0.0;
        break;
      case 3: // Back Right
        driveSparkMax = new CANSparkMax(Drivebase.BACK_RIGHT_DRIVE, MotorType.kBrushless);
        azimuthSparkMax = new CANSparkMax(Drivebase.BACK_RIGHT_TURN, MotorType.kBrushless);
        azimuthCANcoder = new CANcoder(Drivebase.BACK_RIGHT_ENCODER, CANConstants.CANIVORE_NAME);
        absoluteEncoderOffset = 0.481201171875;

        drive_kS = 0;
        drive_kV = 12 / SwerveSubsystem.MAX_LINEAR_SPEED.in(MetersPerSecond);
        drive_kP = 1;
        drive_kD = 0;

        azimuth_kP = 0;
        azimuth_KD = 0;
        break;
      default:
        throw new RuntimeException("Invalid module index");
    }

    // Configure motors
    driveSparkMax.restoreFactoryDefaults();
    azimuthSparkMax.restoreFactoryDefaults();
    Timer.delay(0.1);

    // Set up a timeout for applying settings.
    driveSparkMax.setCANTimeout(250);
    azimuthSparkMax.setCANTimeout(250);

    driveSparkMax.setIdleMode(IdleMode.kBrake);
    driveSparkMax.setSmartCurrentLimit(100);
    azimuthSparkMax.setInverted(Module.AZIMUTH_MOTOR_INVERTED);
    azimuthSparkMax.setIdleMode(IdleMode.kBrake);
    azimuthSparkMax.setSmartCurrentLimit(40);

    // Initialize encoders
    driveEncoder = driveSparkMax.getEncoder();
    azimuthRelativeEncoder = azimuthSparkMax.getEncoder();

    var cancoderConfig = new CANcoderConfiguration();
    cancoderConfig.MagnetSensor.AbsoluteSensorRange = AbsoluteSensorRangeValue.Signed_PlusMinusHalf;
    cancoderConfig.MagnetSensor.MagnetOffset = absoluteEncoderOffset;
    azimuthCANcoder.getConfigurator().apply(cancoderConfig);
    azimuthAbsolutePosition = azimuthCANcoder.getAbsolutePosition();
    azimuthAbsolutePosition.setUpdateFrequency(50);
    azimuthVelocity = azimuthCANcoder.getVelocity();
    azimuthVelocity.setUpdateFrequency(50);
    azimuthCANcoder.optimizeBusUtilization();

    // The motor output in rotations is multiplied by this factor.
    driveEncoder.setPositionConversionFactor(
        Module.DRIVE_WHEEL_CIRCUMFERENCE.in(Meters) / Module.DRIVE_GEAR_RATIO);
    driveEncoder.setVelocityConversionFactor(
        (Module.DRIVE_WHEEL_CIRCUMFERENCE.in(Meters) / Module.DRIVE_GEAR_RATIO) / 60);
    driveEncoder.setPosition(0.0);
    driveEncoder.setMeasurementPeriod(10);
    driveEncoder.setAverageDepth(2);

    azimuthSparkMax.setInverted(Module.AZIMUTH_MOTOR_INVERTED);
    azimuthRelativeEncoder.setPositionConversionFactor(1.0 / Module.AZIMUTH_GEAR_RATIO);
    azimuthRelativeEncoder.setVelocityConversionFactor((1.0 / Module.AZIMUTH_GEAR_RATIO) / 60);
    azimuthRelativeEncoder.setPosition(azimuthAbsolutePosition.getValueAsDouble());
    azimuthRelativeEncoder.setMeasurementPeriod(10);
    azimuthRelativeEncoder.setAverageDepth(2);

    drivePIDController = driveSparkMax.getPIDController();
    drivePIDController.setP(drive_kP);
    drivePIDController.setD(drive_kD);
    driveFF = new SimpleMotorFeedforward(drive_kS, drive_kV);
    driveSparkMax.setClosedLoopRampRate(
        SwerveSubsystem.MAX_LINEAR_SPEED.in(MetersPerSecond)
            / SwerveSubsystem.MAX_LINEAR_ACCELERATION.in(MetersPerSecondPerSecond));

    azimuthPIDController = new PIDController(azimuth_kP, 0, azimuth_KD);
    azimuthPIDController.enableContinuousInput(-0.5, 0.5);

    Timer.delay(0.1);
    driveSparkMax.burnFlash();
    azimuthSparkMax.burnFlash();
    Timer.delay(0.1);

    driveSparkMax.setCANTimeout(50);
    azimuthSparkMax.setCANTimeout(50);

    // Configure CAN frame usage, and disable any unused CAN frames.
    SparkUtils.configureFrameStrategy(
        driveSparkMax,
        Set.of(SparkUtils.Data.VELOCITY, SparkUtils.Data.INPUT, SparkUtils.Data.CURRENT),
        Set.of(SparkUtils.Sensor.INTEGRATED),
        false);
    SparkUtils.configureFrameStrategy(
        azimuthSparkMax,
        Set.of(SparkUtils.Data.VELOCITY, SparkUtils.Data.INPUT, SparkUtils.Data.CURRENT),
        Set.of(SparkUtils.Sensor.INTEGRATED),
        false);

    // Set up high frequency odometry
    driveSparkMax.setPeriodicFramePeriod(
        PeriodicFrame.kStatus2, (int) (1000.0 / Module.ODOMETRY_FREQUENCY));
    timestampQueue = SparkMaxOdometryThread.getInstance().makeTimestampQueue();
    drivePositionQueue =
        SparkMaxOdometryThread.getInstance().registerSignal(driveEncoder::getPosition);
    azimuthPositionQueue =
        SparkMaxOdometryThread.getInstance().registerSignal(azimuthRelativeEncoder::getPosition);

    driveSparkMax.burnFlash();
    azimuthSparkMax.burnFlash();
    setAzimuthBrakeMode(false);
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    inputs.driveAppliedOutput = driveSparkMax.getAppliedOutput();
    // Why does REV not have a stator voltage getter
    inputs.driveAppliedVoltage =
        Volts.of(inputs.driveAppliedOutput * driveSparkMax.getBusVoltage());
    inputs.driveAppliedCurrent = Amps.of(driveSparkMax.getOutputCurrent());
    inputs.drivePosition = Meters.of(driveEncoder.getPosition());
    inputs.driveVelocity = MetersPerSecond.of(driveEncoder.getVelocity());

    BaseStatusSignal.refreshAll(azimuthAbsolutePosition, azimuthVelocity);
    inputs.azimuthAbsolutePosition =
        Rotation2d.fromRotations(azimuthAbsolutePosition.getValueAsDouble());
    // Rotation2d.fromRotations(azimuthRelativeEncoder.getPosition());
    azimuthRelativeEncoder.setPosition(inputs.azimuthAbsolutePosition.getRotations());
    inputs.azimuthVelocity = RotationsPerSecond.of(azimuthVelocity.getValueAsDouble());
    inputs.azimuthAppliedOutput = driveSparkMax.getAppliedOutput();
    inputs.azimuthAppliedVoltage =
        Volts.of(inputs.azimuthAppliedOutput * driveSparkMax.getBusVoltage());
    inputs.azimuthAppliedCurrent = Amps.of(azimuthSparkMax.getOutputCurrent());

    inputs.odometryTimestamps = SwerveUtils.queueToDoubleArray(timestampQueue);
    inputs.odometryDrivePositionsMeters = SwerveUtils.queueToDoubleArray(drivePositionQueue);
    inputs.odometryAzimuthPositions = SwerveUtils.queueToRotation2dArray(azimuthPositionQueue);
  }

  @Override
  public void setDriveVelocity(Measure<Velocity<Distance>> velocity) {
    drivePIDController.setReference(
        velocity.in(MetersPerSecond),
        ControlType.kVelocity,
        0,
        driveFF.calculate(velocity.in(MetersPerSecond)));
  }

  @Override
  public void setRawDrive(double voltage) {
    driveSparkMax.setVoltage(voltage);
  }

  @Override
  public void setRawAzimuth(double voltage) {
    azimuthSparkMax.setVoltage(voltage);
  }

  @Override
  public void setAzimuthPosition(Rotation2d position) {
    BaseStatusSignal.refreshAll(azimuthAbsolutePosition);
    azimuthSparkMax.setVoltage(
        azimuthPIDController.calculate(
            azimuthVelocity.getValueAsDouble(), position.getRotations()));
  }

  @Override
  public void stop() {
    driveSparkMax.stopMotor();
    azimuthSparkMax.stopMotor();
  }

  @Override
  public void setDriveBrakeMode(boolean enable) {
    driveSparkMax.setIdleMode(enable ? IdleMode.kBrake : IdleMode.kCoast);
  }

  @Override
  public void setAzimuthBrakeMode(boolean enable) {
    azimuthSparkMax.setIdleMode(enable ? IdleMode.kBrake : IdleMode.kCoast);
  }
}
