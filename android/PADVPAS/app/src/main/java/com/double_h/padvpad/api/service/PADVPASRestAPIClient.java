package com.double_h.padvpad.api.service;

import com.double_h.padvpad.api.models.Classification;
import com.double_h.padvpad.api.models.Speech;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PADVPASRestAPIClient {
    @POST("classification")
    Call<Classification> classificationForSpeech(@Body Speech speech);
}
