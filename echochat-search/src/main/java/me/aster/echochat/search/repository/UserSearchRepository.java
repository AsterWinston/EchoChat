package me.aster.echochat.search.repository;

import me.aster.echochat.search.entity.UserDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link UserDocument}的Elasticsearch仓库。
 * @author AsterWinston
 */
@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, Long> {
}
