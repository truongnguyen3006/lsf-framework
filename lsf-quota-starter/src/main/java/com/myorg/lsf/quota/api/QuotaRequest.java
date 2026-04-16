package com.myorg.lsf.quota.api;

import lombok.Builder;

import java.time.Duration;

//record giúp khóa cứng dữ liệu từ lúc khởi tạo, đảm bảo tính nguyên vẹn dữ liệu truyền qua kafka, không thể sửa đổi
//đối với kiến trúc EDA mọi event là sự thật diễn ra trong quá khứ nên không thể thay đổi
// => dùng record thay vì dùng class
@Builder
public record QuotaRequest (
    String quotaKey,
    String requestId,
    int amount,
    int limit,
    Duration hold
){

}
