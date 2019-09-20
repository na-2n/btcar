#include <Arduino.h>
#include <stdint.h>
#include <stdlib.h>

// -------------------------------- //

#define SERIAL_BAUDRATE 38400

#define PIN_A_FW 3
#define PIN_A_BW 5

#define PIN_B_FW 9
#define PIN_B_BW 11

#define SPEED_STEP_1 120
#define SPEED_STEP_2 255

// !!-----------------------------!! //

#define SPEED_MIN 70 // don't change unless you know what you're doing

#define CHECK_SPEED // comment to disable check below

// !!-----------------------------!! //

#define PACKET_SIZE 1
#define PACKET_START 0x02

#define READY_BYTE 0x45
#define OK_BYTE 0xC8

typedef struct packet {
    uint8_t pkt_start;

    uint8_t mot_a_state;
    uint8_t mot_a_dir;
    uint8_t mot_a_speed;

    uint8_t mot_b_state;
    uint8_t mot_b_dir;
    uint8_t mot_b_speed;
} packet_t;

void set_motor_speed(uint8_t pin_fw, uint8_t pin_bw, uint8_t negative, uint8_t speed) {
    if (negative) {
        uint8_t pin_tmp = pin_fw;

        pin_fw = pin_bw;
        pin_bw = pin_tmp;
    }

    if (speed < SPEED_MIN) {
        speed = 0;
    }

#ifdef CHECK_SPEED
    if (analogRead(pin_fw) == speed) {
        return;
    }
#endif

    analogWrite(pin_fw, speed);
    analogWrite(pin_bw, LOW);
}

void set_speed_a(uint8_t negative, uint8_t speed) {
    set_motor_speed(PIN_A_FW, PIN_A_BW, negative, speed);
}

void set_speed_b(uint8_t negative, uint8_t speed) {
    set_motor_speed(PIN_B_FW, PIN_B_BW, negative, speed);
}

void handle_packet(packet_t pkt) {
    set_speed_a(pkt.mot_a_dir, pkt.mot_a_speed);
    set_speed_b(pkt.mot_b_dir, pkt.mot_b_speed);
}

void setup() {
    Serial.begin(SERIAL_BAUDRATE);

    Serial.write(READY_BYTE);

    pinMode(PIN_A_FW, OUTPUT);
    pinMode(PIN_A_BW, OUTPUT);

    pinMode(PIN_B_FW, OUTPUT);
    pinMode(PIN_B_BW, OUTPUT);
}

uint8_t get_bit_at(uint8_t byte, uint8_t index) {
    index &= 7;

    return (byte & (0x80 >> index)) >> (7 - index);
}

uint8_t get_bits_at(uint8_t byte, uint8_t index, uint8_t count) {
    index &= 7;
    count &= 7;

    return (byte & (0xFF >> index)) >> ((8 - index) - count);
}

// TODO: fail safe packet reading
void loop() {
    if (Serial.available()) {
        uint8_t data = Serial.read();

        packet_t pkt;

        pkt.pkt_start = get_bits_at(data, 6, 2); // data & (0xFF >> 6);

        Serial.print("start: "); Serial.println(pkt.pkt_start);

        if (pkt.pkt_start != PACKET_START) {
            return;
        }
        
        if (pkt.mot_a_state = get_bit_at(data, 5)) {
            pkt.mot_a_dir = get_bit_at(data, 4);
            pkt.mot_a_speed = get_bit_at(data, 3) ? SPEED_STEP_2 : SPEED_STEP_1;
        } else {
            pkt.mot_a_dir = 0;
            pkt.mot_a_speed = 0;
        }
        
        if (pkt.mot_b_state = get_bit_at(data, 2)) {
            pkt.mot_b_dir = get_bit_at(data, 1);
            pkt.mot_b_speed = get_bit_at(data, 0) ? SPEED_STEP_2 : SPEED_STEP_1;
        } else {
            pkt.mot_b_dir = 0;
            pkt.mot_b_speed = 0;
        }

        Serial.print("state a = "); Serial.println(pkt.mot_a_state);
        Serial.print("speed a = "); Serial.println(pkt.mot_a_speed);
        Serial.print("state b = "); Serial.println(pkt.mot_b_state);
        Serial.print("speed b = "); Serial.println(pkt.mot_b_speed);

        handle_packet(pkt);

        Serial.write(OK_BYTE);
    }
}