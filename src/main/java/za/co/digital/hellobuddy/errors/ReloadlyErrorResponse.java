package za.co.digital.hellobuddy.errors;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public class ReloadlyErrorResponse {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String timeStamp; // Can be a String or LocalDateTime
    private String message;
    private String path;
    private String errorCode;
    private String infoLink;
    private List<Object> details; 
    
    public ReloadlyErrorResponse() {
    }

    // Getters and Setters
    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getInfoLink() {
        return infoLink;
    }

    public void setInfoLink(String infoLink) {
        this.infoLink = infoLink;
    }

    public List<Object> getDetails() {
        return details;
    }

    public void setDetails(List<Object> details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "ReloadlyErrorResponse{" +
                "timeStamp='" + timeStamp + '\'' +
                ", message='" + message + '\'' +
                ", path='" + path + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", infoLink='" + infoLink + '\'' +
                ", details=" + details +
                '}';
    }
}