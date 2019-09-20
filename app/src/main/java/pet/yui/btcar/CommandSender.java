/*
 *     Copyright (C) 2019  Yui
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pet.yui.btcar;

import android.util.Log;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CommandSender {
    private static final String TAG = "CommandSender";

    private static final int PACKET_START = 0x02;
    private static final int TIMER_DELAY = 50;

    private static final int MIN_SPEED = 70;
    private static final int MAX_SPEED = 255;

    private static final int SPEED_STEP_1 = 120;
    private static final int SPEED_STEP_2 = 255;

    private final Map<Motor, Integer> speedQueue;
    private final Map<Motor, Integer> lastSpeeds;
    private final Timer sendTimer;

    private OutputStream out;
    private int deadzone;

    private final TimerTask task = new TimerTask() {
        @Override
        public void run() {
            try {
                byte data = PACKET_START;

                int speedLeft = speedQueue.get(Motor.LEFT);
                int speedRight = speedQueue.get(Motor.RIGHT);

                if (speedLeft == lastSpeeds.get(Motor.LEFT) &&
                    speedRight == lastSpeeds.get(Motor.RIGHT))
                    return;

                lastSpeeds.put(Motor.LEFT, speedLeft);
                lastSpeeds.put(Motor.RIGHT, speedRight);

                if (speedRight < 0) {
                    data |= (0x80 >> 4);

                    speedRight = -speedRight;
                }

                if (speedRight > 0) {
                    data |= (0x80 >> 5);

                    if (speedRight >= 255)
                        data |= (0x80 >> 3);
                }

                if (speedLeft < 0) {
                    data |= (0x80 >> 1);

                    speedLeft = -speedLeft;
                }
                if (speedLeft > 0) {
                    data |= (0x80 >> 2);

                    if (speedLeft >= 255)
                        data |= 0x80;
                }

                out.write(data);
            } catch(Exception e) {
                Log.d(TAG, "Error while trying to send data", e);
            }
        }
    };

    public CommandSender() {
        this(null, 0);
    }

    public CommandSender(OutputStream out, int deadzone) {
        this.out = out;
        this.deadzone = deadzone;

        speedQueue = new HashMap<>();
        lastSpeeds = new HashMap<>();

        speedQueue.put(Motor.LEFT, 0);
        speedQueue.put(Motor.RIGHT, 0);

        lastSpeeds.put(Motor.LEFT, 0);
        lastSpeeds.put(Motor.RIGHT, 0);

        sendTimer = new Timer();

        sendTimer.scheduleAtFixedRate(task, TIMER_DELAY, TIMER_DELAY);
    }

    public void setOuput(OutputStream out) {
        this.out = out;
    }

    public void setDeadzone(int deadzone) {
        this.deadzone = deadzone;
    }

    public void sendInput(Motor motor, int strength, int angle) {
        int deadStrength = strength - deadzone;
        int speed = deadStrength > 0
                ? ((strength / deadStrength) * (deadStrength * (MAX_SPEED / 100)))
                : 0;

        if (speed < MIN_SPEED)
            speed = 0;

        if (speed != 0)
            speed = speed > 120 ? SPEED_STEP_2 : SPEED_STEP_1;

        if (angle > 180 && angle < 360)
            speed = -speed;

        if (speed == lastSpeeds.get(motor))
            return;

        speedQueue.put(motor, speed);
    }

    public enum Motor {
        LEFT,
        RIGHT
    }
}
