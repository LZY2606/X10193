package court;

import court.domain.Scheme;
import court.store.SchemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DbgRepoTest {
    @Autowired SchemeRepository repo;
    @Test
    void t() {
        Scheme s = repo.getScheme(2L);
        System.out.println("NULL? reportJson=" + (s.reportJson == null) + " policy=" + s.policy
                + " order=" + s.loadOrder);
    }
}
