package com.fmi.global.apiPayload.exception.handler;


import com.fmi.global.apiPayload.code.BaseErrorCode;
import com.fmi.global.apiPayload.exception.GeneralException;

public class S3Handler extends GeneralException {
  public S3Handler(BaseErrorCode errorCode){
    super(errorCode);
  }
}
