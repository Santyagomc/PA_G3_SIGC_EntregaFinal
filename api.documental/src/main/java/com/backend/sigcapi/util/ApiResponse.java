package com.backend.sigcapi.util;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL) // evita mostrar campos nulos
public class ApiResponse<T> {

    private boolean exito;
    private String mensaje;
    private T data;

    public ApiResponse(boolean exito, String mensaje) {
        this.exito = exito;
        this.mensaje = mensaje;
    }

    public ApiResponse(boolean exito, String mensaje, T data) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.data = data;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
