package apincer.music.core.repository;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.lang.reflect.Proxy;

import apincer.music.core.model.SearchCriteria;
import apincer.music.core.repository.spi.DbHelper;

public class TagRepositoryQueryErrorTest {

    @Test
    public void findMusic_queryFailure_propagatesInsteadOfEmptyList() {
        DbHelper failing = (DbHelper) Proxy.newProxyInstance(
                DbHelper.class.getClassLoader(),
                new Class<?>[]{DbHelper.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("database unavailable");
                });
        TagRepository repository = new TagRepository(null, failing);
        SearchCriteria criteria = new SearchCriteria(SearchCriteria.TYPE.LIBRARY);
        criteria.setSearchText("anything");

        assertThrows(IllegalStateException.class, () -> repository.findMusic(criteria, 0, 50));
    }
}
