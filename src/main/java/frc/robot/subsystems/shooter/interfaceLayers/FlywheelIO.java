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

package frc.robot.subsystems.shooter.interfaceLayers;

import static edu.wpi.first.units.Units.*;

import edu.wpi.first.units.*;
import org.littletonrobotics.junction.AutoLog;

public interface FlywheelIO {
  @AutoLog
  public class ShooterIOInputs {
    /** The position of the shooter flywheel. */
    public Measure<Angle> position = Rotations.of(0);
    /** The velocity of the shooter flywheel. */
    public Measure<Velocity<Angle>> velocity = RotationsPerSecond.of(0);
    /** The total output applied to the motor by the closed loop control. */
    public double appliedOutput = 0;
    /** The voltage applied to the motor by the motor controller. */
    public Measure<Voltage> appliedVoltage = Volts.of(0);
    /** The current applied to the motor by the motor controller. */
    public Measure<Current> appliedCurrent = Amps.of(0);
  }

  /** Updates the set of loggable inputs. */
  public default void updateInputs(ShooterIOInputs inputs) {}

  /** Spins the shooter flywheel up. */
  public default void run() {}

  /** Stops the shooter flywheel. */
  public default void stop() {}

  /** Gets the position of the shooter flywheel. */
  public default Measure<Angle> getPosition() {
    return Rotations.of(0);
  }

  /** Gets the velocity of the shooter flywheel. */
  public default Measure<Velocity<Angle>> getVelocity() {
    return RotationsPerSecond.of(0);
  }
}
