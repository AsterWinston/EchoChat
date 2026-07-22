package me.aster.echochat.search.repository;

import me.aster.echochat.search.entity.MessageDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link MessageDocument}的Elasticsearch仓库。
 * @author AsterWinston
 */
@Repository
public interface MessageSearchRepository extends ElasticsearchRepository<MessageDocument, Long> {
}
