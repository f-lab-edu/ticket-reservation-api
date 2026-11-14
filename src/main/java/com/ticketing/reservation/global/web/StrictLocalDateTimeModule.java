package com.ticketing.reservation.global.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class StrictLocalDateTimeModule extends SimpleModule {

  public StrictLocalDateTimeModule() {
    addDeserializer(
        LocalDateTime.class, new StringOnlyDeserializer(LocalDateTimeDeserializer.INSTANCE));
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.configOverride(LocalDateTime.class).setFormat(JsonFormat.Value.forLeniency(false));
  }

  private static class StringOnlyDeserializer extends StdDeserializer<LocalDateTime>
      implements ContextualDeserializer {

    private final JsonDeserializer<?> delegate;

    StringOnlyDeserializer(JsonDeserializer<?> delegate) {
      super(LocalDateTime.class);
      this.delegate = delegate;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      if (delegate instanceof ContextualDeserializer contextual) {
        return new StringOnlyDeserializer(contextual.createContextual(ctxt, property));
      }
      return this;
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      if (!p.hasToken(JsonToken.VALUE_STRING)) {
        return (LocalDateTime) ctxt.handleUnexpectedToken(LocalDateTime.class, p);
      }
      return (LocalDateTime) delegate.deserialize(p, ctxt);
    }
  }
}
