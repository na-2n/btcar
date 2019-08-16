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

#include <stdint.h>
#include <stdbool.h>

#include <Arduino.h>

#define LEFT_A 3
#define RIGHT_A 5

#define LEFT_B 9
#define RIGHT_B 11

#define BAUDRATE 9600

#define MIN_SPEED 70
#define MAX_SPEED 255

int16_t last_speed_a;
int16_t last_speed_b;

void setup() {
    Serial.begin(BAUDRATE);

    pinMode(LEFT_A, OUTPUT);
    pinMode(RIGHT_A, OUTPUT);
}

enum motors {
    MOTOR_A,
    MOTOR_B
};

enum commands {
    COMMAND_SPEED_LEFT = 'L',
    COMMAND_SPEED_RIGHT = 'R',
};

void handle_input(String str) {
    char command = str[0];

    String arg = str.substring(1);

    int16_t speed = strtol(arg.c_str(), NULL, 0);

    switch (command) {
        case COMMAND_SPEED_LEFT: {
            if (speed == last_speed_b) {
                return;
            }

            set_speed(speed, MOTOR_B);

            last_speed_b = speed;

            break;
        }

        case COMMAND_SPEED_RIGHT: {
            if (speed == last_speed_a) {
                return;
            }

            set_speed(speed, MOTOR_A);

            last_speed_a = speed;

            break;
        }
    }
}

void set_speed(int16_t speed, uint8_t motor) {
    bool backwards = speed < 0;

    if (backwards) {
        speed = -speed;
    }

    if (speed > MAX_SPEED) {
        speed = MAX_SPEED;
    } else if (speed > 0 && speed < MIN_SPEED) {
        speed = MIN_SPEED;
    }

    if (backwards) {
        set_speed_backward(speed, motor);
    } else {
        set_speed_forward(speed, motor);
    }
}

void set_speed_forward(uint8_t speed, uint8_t motor) {
    switch (motor) {
        case MOTOR_A: {
            analogWrite(RIGHT_A, LOW);
            analogWrite(LEFT_A, speed);

            break;
        }

        case MOTOR_B: {
            analogWrite(RIGHT_B, LOW);
            analogWrite(LEFT_B, speed);

            break;
        }
    }
}

void set_speed_backward(uint8_t speed, uint8_t motor) {
    switch (motor) {
        case MOTOR_A: {
            analogWrite(LEFT_A, LOW);
            analogWrite(RIGHT_A, speed);

            break;
        }

        case MOTOR_B: {
            analogWrite(LEFT_B, LOW);
            analogWrite(RIGHT_B, speed);

            break;
        }
    }
}

void loop() {
    if (Serial.available()) {
        String str = Serial.readStringUntil(';');

        if (str) {
            handle_input(str);
        }
    }
}
