package br.com.tekcondo.reservation;

import java.time.LocalTime;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Hora local do condomínio gravada como minutos desde a meia-noite. Evita o deslocamento de fuso que o Hibernate
 * aplica a {@code LocalTime} quando {@code hibernate.jdbc.time_zone} está configurado (UTC neste projeto).
 */
@Converter
public class MinuteOfDay implements AttributeConverter<LocalTime, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : time.getHour() * 60 + time.getMinute();
    }

    @Override
    public LocalTime convertToEntityAttribute(Integer minutes) {
        return minutes == null ? null : LocalTime.of(minutes / 60, minutes % 60);
    }
}
