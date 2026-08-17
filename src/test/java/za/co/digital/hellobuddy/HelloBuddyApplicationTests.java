package za.co.digital.hellobuddy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.ai.vertexai.gemini.project-id=test-project-id",
    "spring.ai.vertexai.gemini.location=us-central1",
    "paystack.api.key=sk_test_c84a0f8e4b67a575bbd043d81abf20ef4aef54ba",
    "stripe.api.key=sk_test_51TnMSGQiqRLgEPsewUF3BAIOHfKJutb37EN7fk7PXQyW1yKyeTNdivhGYDtBQxyXKxzZWxwYpUp1fXLYl7QH8CYO00TRu9rBBn",
    "hello.buddy.url=https://localhost:9091",
    "paystack.initialize.url=https://api.paystack.co/transaction/initialize",
    "platform.markup=0.065",
    "south.african.fx=15.3500004",
    "status.cron.schedular=0 */5 * * * *",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.cache.type=redis",
    "spring.cache.redis.time-to-live=600000",
    "spring.cache.redis.cache-null-values=false",
    "stripe.fixed.fee=0.065",
    "stripe.percentage.fee=0.029",
    "strip.minimum.amount=0.065",
    "south.african.vat=0.15",
    "south.afican.vat.multiplier=1.15",
    "paystack.fixed.fee=1",
    "paystack.fee.percentage-inc-vat=0.03335",
    "paystack.fee.fixed-inc-vat=1.15",
    "customer.default.email=jephreycuma@hellobuddy.africa",
    "logging.level.org.hibernate.SQL=DEBUG",
    "logging.level.org.hibernate.type.descriptor.sql=TRACE",
    "spring.jpa.show-sql=true",
    "wallet.root.username=jephreycuma",
    "spring.datasource.url=jdbc:h2:mem:hellobuddy;DB_CLOSE_DELAY=-1;MODE=MariaDB",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.MariaDBDialect"
})
@EnableAutoConfiguration(exclude = {
    org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration.class
})
class HelloBuddyApplicationTests {

    @MockBean
    private ChatModel chatModel;

    @Test
    void contextLoads() {
    }
}