package br.com.tekcondo.reserva;

import java.time.LocalTime;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Hora local do condomínio gravada como minutos desde a meia-noite. Evita o deslocamento de fuso que o Hibernate
 * aplica a {@code LocalTime} quando {@code hibernate.jdbc.time_zone} está configurado (UTC neste projeto).
 */
@Converter
public class MinutoDoDia implements AttributeConverter<LocalTime, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LocalTime hora) {
        return hora == null ? null : hora.getHour() * 60 + hora.getMinute();
    }

    @Override
    public LocalTime convertToEntityAttribute(Integer minutos) {
        return minutos == null ? null : LocalTime.of(minutos / 60, minutos % 60);
    }
}
