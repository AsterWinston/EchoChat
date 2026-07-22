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
 * user_index的Elasticsearch文档映射，存储用户资料数据以支持按昵称全文搜索。
 * @author AsterWinston
 */
@Data
@Document(indexName = "user_index")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDocument {

    /** 主键 —— 用户ID */
    @Id
    @Field(type = FieldType.Long)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 支持全文搜索的显示名称 */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String nickname;

    /** 精确匹配的邮箱地址 */
    @Field(type = FieldType.Keyword)
    private String email;
}
