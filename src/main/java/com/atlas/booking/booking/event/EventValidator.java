package com.atlas.booking.booking.event;

import com.atlas.booking.shared.messaging.EventEnvelope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventValidator {

  private final Validator validator;

  public <T> void validate(EventEnvelope<T> envelope) {

    Set<ConstraintViolation<EventEnvelope<T>>> violations =
        validator.validate(envelope);

    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
