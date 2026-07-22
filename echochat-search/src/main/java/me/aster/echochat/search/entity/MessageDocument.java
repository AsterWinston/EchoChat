package me.aster.echochat.search.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import me.aster.echochat.common.constant.RedisKeyConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 表示一条已索引的聊天消息的Elasticsearch文档。
 * 忽略未知的JSON字段（如status、seq），这些字段是源Message实体的一部分，
 * 但搜索索引不需要。
 * @author AsterWinston
 */
@Data
@Document(indexName = RedisKeyConstants.ES_INDEX_PREFIX)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDocument {

    /** 主键 —— 消息ID */
    @Id
    @Field(type = FieldType.Long)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long msgId;

    @Field(type = FieldType.Keyword)
    private String sessionType;

    @Field(type = FieldType.Long)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUid;

    /** 接收方ID（用户或群组） */
    @Field(type = FieldType.Keyword)
    private String toId;

    /** 消息内容类型（如"TEXT"、"IMAGE"） */
    @Field(type = FieldType.Keyword)
    private String msgType;

    /** 支持全文搜索的消息正文 */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    /** 用于过滤的组合会话标识符（sessionType_toId） */
    @Field(type = FieldType.Keyword)
    private String sessionId;

    /** 消息创建时间戳，ISO字符串格式 */
    @Field(type = FieldType.Keyword)
    private String createdAt;

    @Field(type = FieldType.Integer)
    private Integer isRecalled;

    /** 解析后的发送者昵称（查询时富化，不做索引） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String fromName;

    /** 解析后的目标名称 —— 单聊为用户昵称，群聊为群名称（查询时富化） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String toName;
}
