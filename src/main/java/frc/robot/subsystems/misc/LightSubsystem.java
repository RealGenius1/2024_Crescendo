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

package frc.robot.subsystems.misc;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.subsystems.misc.interfaceLayers.LightsIO;
import frc.robot.subsystems.misc.interfaceLayers.LightsIOBlinkin.Colors;

public class LightSubsystem extends SubsystemBase {

  LightsIO io;

  public LightSubsystem(LightsIO io) {
    this.io = io;
    io.setColor(Colors.GREEN);
  }

  public void setColor(Colors color) {
    io.setColor(color);
  }

  public Command setColorCommand(Colors color) {
    return runOnce(() -> setColor(color));
  }

  public void setColorValue(int num) {
    io.setColorValue(num);
  }

  public Command setColorValueCommand(int num) {
    return runOnce(() -> setColorValue(num));
  }

  public void setColorNull() {
    io.setColorNull();
  }
}
