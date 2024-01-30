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

package frc.robot.subsystems.swerve;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.MotorOutputConfigs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.MotionMagicTorqueCurrentFOC;
import com.ctre.phoenix6.controls.MotionMagicVelocityTorqueCurrentFOC;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.AbsoluteSensorRangeValue;
import com.ctre.phoenix6.signals.FeedbackSensorSourceValue;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;
import java.util.Queue;

/**
 * Module IO implementation for Talon FX drive motor controller, Talon FX turn motor controller, and
 * CANcoder
 *
 * <p>NOTE: This implementation should be used as a starting point and adapted to different hardware
 * configurations (e.g. If using an analog encoder, copy from "ModuleIOSparkMax")
 *
 * <p>To calibrate the absolute encoder offsets, point the modules straight (such that forward
 * motion on the drive motor will propel the robot forward) and copy the reported values from the
 * absolute encoders using AdvantageScope. These values are logged under
 * "/Drive/ModuleX/TurnAbsolutePosition"
 */
public class ModuleIOTalonFX implements ModuleIO {
  private final TalonFX driveTalon;
  private final TalonFX turnTalon;
  private final CANcoder cancoder;

  private final Queue<Double> timestampQueue;

  private final StatusSignal<Double> drivePosition;
  private final Queue<Double> drivePositionQueue;
  private final StatusSignal<Double> driveVelocity;
  private final StatusSignal<Double> driveAppliedVolts;
  private final StatusSignal<Double> driveClosedLoopOutput;
  private final StatusSignal<Double> driveCurrent;

  private final StatusSignal<Double> turnAbsolutePosition;
  private final StatusSignal<Double> turnPosition;
  private final Queue<Double> turnPositionQueue;
  private final StatusSignal<Double> turnVelocity;
  private final StatusSignal<Double> turnAppliedVolts;
  private final StatusSignal<Double> turnClosedLoopOutput;
  private final StatusSignal<Double> turnCurrent;

  private final boolean isTurnMotorInverted = true;
  private final Rotation2d absoluteEncoderOffset;

  public ModuleIOTalonFX(int index) {
    switch (index) {
      case 0:
        driveTalon = new TalonFX(0, "drivebase");
        turnTalon = new TalonFX(1, "drivebase");
        cancoder = new CANcoder(2, "drivebase");
        absoluteEncoderOffset = Rotation2d.fromDegrees(0); // TODO: Calibrate
        break;
      case 1:
        driveTalon = new TalonFX(3, "drivebase");
        turnTalon = new TalonFX(4, "drivebase");
        cancoder = new CANcoder(5, "drivebase");
        absoluteEncoderOffset = Rotation2d.fromDegrees(0); // TODO: Calibrate
        break;
      case 2:
        driveTalon = new TalonFX(6, "drivebase");
        turnTalon = new TalonFX(7, "drivebase");
        cancoder = new CANcoder(8, "drivebase");
        absoluteEncoderOffset = Rotation2d.fromDegrees(0); // TODO: Calibrate
        break;
      case 3:
        driveTalon = new TalonFX(9, "drivebase");
        turnTalon = new TalonFX(10, "drivebase");
        cancoder = new CANcoder(11, "drivebase");
        absoluteEncoderOffset = Rotation2d.fromDegrees(0); // TODO: Calibrate
        break;
      default:
        throw new RuntimeException("Invalid module index");
    }

    var driveConfig = new TalonFXConfiguration();
    driveConfig.CurrentLimits.StatorCurrentLimit = 100.0;
    driveConfig.CurrentLimits.StatorCurrentLimitEnable = true;
    driveConfig.Feedback.SensorToMechanismRatio =
        Module.DRIVE_GEAR_RATIO * Module.DRIVE_WHEEL_CIRCUMFERENCE;
    driveConfig.Slot0.kP = 0.05; // % output per m/s of error
    driveConfig.Slot0.kI = 0; // % output per m of integrated error
    driveConfig.Slot0.kD = 0; // % output per m/s^2 of error derivative
    driveConfig.Slot0.kS = 0; // Amps of additional current needed to overcome friction
    driveConfig.Slot0.kV = 0; // Amps of additional current per m/s of velocity setpoint
    driveConfig.Slot0.kA = 0; // Amps of additional current per m/s^2 of acceleration setpoint
    driveConfig.MotionMagic.MotionMagicAcceleration = 14; // Max allowed acceleration, in m/s^2
    driveConfig.MotionMagic.MotionMagicJerk = 140; // Max allowed jerk, in m/s^3
    driveTalon.getConfigurator().apply(driveConfig);
    setDriveBrakeMode(true);

    var turnConfig = new TalonFXConfiguration();
    turnConfig.CurrentLimits.StatorCurrentLimit = 40.0;
    turnConfig.CurrentLimits.StatorCurrentLimitEnable = true;
    turnConfig.Feedback.SensorToMechanismRatio = 1;
    turnConfig.Feedback.RotorToSensorRatio = Module.TURN_GEAR_RATIO;
    turnConfig.Feedback.FeedbackSensorSource = FeedbackSensorSourceValue.FusedCANcoder;
    turnConfig.Feedback.FeedbackRemoteSensorID = cancoder.getDeviceID();
    turnConfig.Slot0.kP = 1; // % output per rotation of error
    turnConfig.Slot0.kI = 0; // % output per rotation of integrated error
    turnConfig.Slot0.kD = 0; // % output per rotations/s of error derivative
    turnConfig.Slot0.kS = 0; // Amps of additional current needed to overcome friction
    turnConfig.Slot0.kV = 0; // Amps of additional current per rot/s of velocity setpoint
    turnConfig.Slot0.kA = 0; // Amps of additional current per rot/s^2 of acceleration setpoint
    turnConfig.MotionMagic.MotionMagicAcceleration = 5; // Max allowed acceleration, in rot/s^2
    turnConfig.MotionMagic.MotionMagicJerk = 50; // Max allowed jerk, in rot/s^3
    turnConfig.ClosedLoopGeneral.ContinuousWrap = true;
    turnTalon.getConfigurator().apply(turnConfig);
    setTurnBrakeMode(true);

    var cancoderConfig = new CANcoderConfiguration();
    cancoderConfig.MagnetSensor.AbsoluteSensorRange = AbsoluteSensorRangeValue.Signed_PlusMinusHalf;
    cancoderConfig.MagnetSensor.MagnetOffset = absoluteEncoderOffset.getRotations();
    cancoderConfig.MagnetSensor.SensorDirection = SensorDirectionValue.CounterClockwise_Positive;
    cancoder.getConfigurator().apply(cancoderConfig);

    timestampQueue = PhoenixOdometryThread.getInstance().makeTimestampQueue();

    drivePosition = driveTalon.getPosition();
    drivePositionQueue =
        PhoenixOdometryThread.getInstance().registerSignal(driveTalon, driveTalon.getPosition());
    driveVelocity = driveTalon.getVelocity();
    driveAppliedVolts = driveTalon.getMotorVoltage();
    driveClosedLoopOutput = driveTalon.getClosedLoopOutput();
    driveCurrent = driveTalon.getStatorCurrent();

    turnAbsolutePosition = cancoder.getAbsolutePosition();
    turnPosition = turnTalon.getPosition();
    turnPositionQueue =
        PhoenixOdometryThread.getInstance().registerSignal(turnTalon, turnTalon.getPosition());
    turnVelocity = turnTalon.getVelocity();
    turnAppliedVolts = turnTalon.getMotorVoltage();
    turnClosedLoopOutput = turnTalon.getClosedLoopOutput();
    turnCurrent = turnTalon.getStatorCurrent();

    BaseStatusSignal.setUpdateFrequencyForAll(
        Module.ODOMETRY_FREQUENCY, drivePosition, turnPosition);
    BaseStatusSignal.setUpdateFrequencyForAll(
        50.0,
        driveVelocity,
        driveAppliedVolts,
        driveClosedLoopOutput,
        driveCurrent,
        turnAbsolutePosition,
        turnVelocity,
        turnAppliedVolts,
        turnClosedLoopOutput,
        turnCurrent);
    driveTalon.optimizeBusUtilization();
    turnTalon.optimizeBusUtilization();
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    BaseStatusSignal.refreshAll(
        drivePosition,
        driveVelocity,
        driveAppliedVolts,
        driveClosedLoopOutput,
        driveCurrent,
        turnAbsolutePosition,
        turnPosition,
        turnVelocity,
        turnAppliedVolts,
        turnClosedLoopOutput,
        turnCurrent);

    inputs.drivePositionMeters = drivePosition.getValueAsDouble();
    inputs.driveVelocityMetersPerSec = driveVelocity.getValueAsDouble();
    inputs.driveAppliedVolts = driveAppliedVolts.getValueAsDouble();
    inputs.driveAppliedDutyCycle = driveClosedLoopOutput.getValueAsDouble();
    inputs.driveCurrentAmps = new double[] {driveCurrent.getValueAsDouble()};

    inputs.turnAbsolutePosition = Rotation2d.fromRotations(turnAbsolutePosition.getValueAsDouble());
    inputs.turnVelocityRadPerSec = Units.rotationsToRadians(turnVelocity.getValueAsDouble());
    inputs.turnAppliedVolts = turnAppliedVolts.getValueAsDouble();
    inputs.turnAppliedDutyCycle = turnClosedLoopOutput.getValueAsDouble();
    inputs.turnCurrentAmps = new double[] {turnCurrent.getValueAsDouble()};

    inputs.odometryTimestamps =
        timestampQueue.stream().mapToDouble((Double value) -> value).toArray();
    inputs.odometryDrivePositionsMeters =
        drivePositionQueue.stream().mapToDouble((Double value) -> value).toArray();
    inputs.odometryTurnPositions =
        turnPositionQueue.stream()
            .map((Double value) -> Rotation2d.fromRotations(value))
            .toArray(Rotation2d[]::new);
    timestampQueue.clear();
    drivePositionQueue.clear();
    turnPositionQueue.clear();
  }

  @Override
  public void setDriveVelocity(double velocity) {
    driveTalon.setControl(new MotionMagicVelocityTorqueCurrentFOC(velocity));
  }

  @Override
  public void setTurnPosition(Rotation2d position) {
    turnTalon.setControl(new MotionMagicTorqueCurrentFOC(position.getRotations()));
  }

  @Override
  public void stop() {
    driveTalon.stopMotor();
    turnTalon.stopMotor();
  }

  @Override
  public void setDriveBrakeMode(boolean enable) {
    var config = new MotorOutputConfigs();
    config.Inverted = InvertedValue.CounterClockwise_Positive;
    config.NeutralMode = enable ? NeutralModeValue.Brake : NeutralModeValue.Coast;
    driveTalon.getConfigurator().apply(config);
  }

  @Override
  public void setTurnBrakeMode(boolean enable) {
    var config = new MotorOutputConfigs();
    config.Inverted =
        isTurnMotorInverted
            ? InvertedValue.Clockwise_Positive
            : InvertedValue.CounterClockwise_Positive;
    config.NeutralMode = enable ? NeutralModeValue.Brake : NeutralModeValue.Coast;
    turnTalon.getConfigurator().apply(config);
  }
}
