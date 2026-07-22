package me.aster.echochat.search.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.search.entity.GroupDocument;
import me.aster.echochat.search.entity.UserDocument;
import me.aster.echochat.search.repository.GroupSearchRepository;
import me.aster.echochat.search.repository.UserSearchRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索模块的内部REST控制器，接收来自其他微服务的索引请求，
 * 用于更新Elasticsearch中的用户和群组文档。
 * @author AsterWinston
 */
@Slf4j
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final UserSearchRepository userSearchRepository;
    private final GroupSearchRepository groupSearchRepository;

    @PostMapping("/index/user")
    public void indexUser(@RequestBody Map<String, Object> body) {
        try {
            UserDocument doc = new UserDocument();
            Object uid = body.get("uid");
            doc.setUid(uid instanceof Number ? ((Number) uid).longValue() : Long.parseLong(String.valueOf(uid)));
            doc.setNickname((String) body.get("nickname"));
            doc.setEmail((String) body.get("email"));
            userSearchRepository.save(doc);
            log.info("User indexed to ES (feign): uid={}", doc.getUid());
        } catch (Exception e) {
            log.error("Failed to index user via feign", e);
        }
    }

    @PostMapping("/index/group")
    public void indexGroup(@RequestBody Map<String, Object> body) {
        try {
            GroupDocument doc = new GroupDocument();
            Object gid = body.get("gid");
            doc.setGid(gid instanceof Number ? ((Number) gid).longValue() : Long.parseLong(String.valueOf(gid)));
            doc.setName((String) body.get("name"));
            Object ownerUid = body.get("ownerUid");
            doc.setOwnerUid(ownerUid instanceof Number ? ((Number) ownerUid).longValue() : Long.parseLong(String.valueOf(ownerUid)));
            doc.setAvatar((String) body.get("avatar"));
            groupSearchRepository.save(doc);
            log.info("Group indexed to ES (feign): gid={}", doc.getGid());
        } catch (Exception e) {
            log.error("Failed to index group via feign", e);
        }
    }
}
