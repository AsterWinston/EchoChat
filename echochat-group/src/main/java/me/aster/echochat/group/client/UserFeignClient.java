package me.aster.echochat.group.client;

import me.aster.echochat.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * echochat-user服务的Feign客户端，用于验证用户是否存在。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-user", path = "/internal/user")
public interface UserFeignClient {

    /**
     * 根据UID获取用户信息，用于验证和资料查询。
     *
     * @param uid 用户ID
     * @return 用户实体
     */
    @GetMapping("/{uid}")
    User getUserByUid(@PathVariable Long uid);
}
