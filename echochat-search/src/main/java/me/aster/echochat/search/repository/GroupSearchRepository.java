package me.aster.echochat.search.repository;

import me.aster.echochat.search.entity.GroupDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link GroupDocument}的Spring Data Elasticsearch仓库。
 * @author AsterWinston
 */
@Repository
public interface GroupSearchRepository extends ElasticsearchRepository<GroupDocument, Long> {
}
