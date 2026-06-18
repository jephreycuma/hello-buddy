package za.co.digital.hellobuddy.dto;

public class ReloadlyTopupResult {
	private int statusCode;
    private String rawBody;
    private TopupResponse topupResponse;

    public ReloadlyTopupResult() {}

    // 2. Convenience constructor used by your service layer
    public ReloadlyTopupResult(java.net.http.HttpResponse<String> httpResponse, TopupResponse topupResponse) {
        this.statusCode = httpResponse != null ? httpResponse.statusCode() : -1;
        this.rawBody = httpResponse != null ? httpResponse.body() : "";
        this.topupResponse = topupResponse;
    }

    // 3. Standard Getters and Setters
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public TopupResponse getTopupResponse() { return topupResponse; }
    public void setTopupResponse(TopupResponse topupResponse) { this.topupResponse = topupResponse; }

    // Helper method
    public boolean isSuccessful() { 
        return statusCode == 200 || statusCode == 201; 
    }

}