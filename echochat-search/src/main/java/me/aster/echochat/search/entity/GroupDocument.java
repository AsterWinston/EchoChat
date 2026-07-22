package me.aster.echochat.search.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * group_index的Elasticsearch文档映射，存储群组元数据以支持按群名称全文搜索。
 * @author AsterWinston
 */
@Data
@Document(indexName = "group_index")
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDocument {

    @Id
    @Field(type = FieldType.Long)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long gid;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Long)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUid;

    @Field(type = FieldType.Keyword)
    private String avatar;
}
