package com.londonmeet.common.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Unified API response wrapper.
 *
 * @param <T> response data type
 */
@Data
public class ApiResponse<T> implements Serializable {

    /**
     * Business status code.
     * 200 = success
     * 500 = error
     */
    private Integer code;

    /**
     * Response message.
     */
    private String message;

    /**
     * Response data.
     */
    private T data;

    /**
     * Success response without data.
     */
    public static <T> ApiResponse<T> success() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Success");
        return response;
    }

    /**
     * Success response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Success");
        response.setData(data);
        return response;
    }

    /**
     * Error response.
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    /**
     * Error response with business status code.
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
