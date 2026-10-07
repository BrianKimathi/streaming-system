package com.streamx.trending;

import com.streamx.trending.client.CatalogClient;
import com.streamx.trending.service.TrendingDecayJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trending_ctx;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TrendingApplicationContextTest {

    @Autowired
    private CatalogClient catalogClient;

    @Autowired
    private TrendingDecayJob decayJob;

    @Test
    void contextLoadsWithCatalogClientAndScheduledJob() {
        assertNotNull(catalogClient);
        assertNotNull(decayJob);
    }
}
