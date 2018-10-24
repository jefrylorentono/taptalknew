package com.moselo.HomingPigeon.Model.ResponseModel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HpCommonResponse {
    @JsonProperty("success") Boolean success;
    @JsonProperty("message") String message;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}