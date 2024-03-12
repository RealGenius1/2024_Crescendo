# Children of the Corn 2024 Robot Code

Code written by [FIRST Robotics Competition Team 167, Children of the Corn](https://github.com/icrobotics-team167) for the 2024 FRC season.

AdvantageKit's "log literally everything" approach is used here, to allow for extremely robust debugging and simulations. In addition, it supports high frequency odometry and vision-based pose estimation, giving it high confidence in knowing where the robot is on the field at all times.

## License

This project uses the GNU-GPL v3 open-source license. Due to the nature of the GNU-GPL v3 license, we cannot *under any circumstances*, provide warranty for the function of this code. For more information, read the `LICENSE` file or read a summary of the license [here](https://choosealicense.com/licenses/gpl-3.0/).

## General Usage

- Always commit any changes before deploying code to the robot.
  - When committing changes, always include the name of the person committing in the commit description. The commit description should also contain details of what actually changed in the commit.
- [The Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) is enforced via the [Spotless](https://github.com/diffplug/spotless) plugin for gradle. It is recommended to use the [Spotless Gradle](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle) extension for VSCode as your Java formatter so that the code style guidelines are always followed.
- Programmers should use [AdvantageScope](https://github.com/Mechanical-Advantage/AdvantageScope) for telemetry data, and drivers should use [Elastic](https://github.com/Gold872/elastic-dashboard) for match data.

## Known issues

- VSCode's highlighter is known to kinda... forget where classes are, and this happens most often with classes autogenerated by AdvantageKit. (We once had an issue where the highlighter said that `String` doesn't exist) To fix this, compile the code, reload VScode a bunch, and if those both fail, open the command palette and run `Java: Clean Java Language Server Workspace`.

## Credits

Contributors:

- [Tada Goto](https://github.com/TheComputer314)
- [Spencer Thomas](https://github.com/RealGenius1)

Code taken from the following places:

- [AdvantageKit Advanced Swerve Example](https://github.com/Mechanical-Advantage/AdvantageKit/tree/main/example_projects)
  - Uses [Measure](https://docs.wpilib.org/en/stable/docs/software/basic-programming/java-units.html) to improve readability and eliminating unit confusion.
- [PathPlanner AdvantageKit-Compatible Pathfinding](https://gist.github.com/mjansen4857/a8024b55eb427184dbd10ae8923bd57d)
- [Sciborgs Spark Configuration Utilities](https://github.com/SciBorgs/Hydrogen/blob/main/src/main/java/org/sciborgs1155/lib/SparkUtils.java)
- See Git commit history for specifics on changes.
