package za.co.digital.hellobuddy.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

	@Value("${spring.ai.openai.api-key:mock-api-key}")
	private String apiKey;

	@Value("${spring.ai.openai.base-url:https://api.groq.com/openai}")
	private String baseUrl;

	@Value("${spring.ai.openai.chat.options.model:llama-3.3-70b-versatile}")
	private String model;

	@Value("${spring.ai.openai.chat.options.temperature:0.7}")
	private Float temperature;

    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel() {
        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl,
                apiKey
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}