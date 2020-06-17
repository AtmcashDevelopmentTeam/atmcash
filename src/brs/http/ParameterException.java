package brs.http;

import brs.AtmException;
import com.google.gson.JsonElement;

public final class ParameterException extends AtmException {

  private transient final JsonElement errorResponse;

  public ParameterException(JsonElement errorResponse) {
    this.errorResponse = errorResponse;
  }

  JsonElement getErrorResponse() {
    return errorResponse;
  }

}
