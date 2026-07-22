package me.aster.echochat.group.service;

import java.util.Map;

/**
 * 群组邀请链接操作的服务接口。
 * @author AsterWinston
 */
public interface InviteService {

    /**
     * 创建群组邀请链接。
     *
     * @param uid         创建邀请的UID
     * @param gid         群组ID
     * @param expireHours 有效时长（小时），上限72
     * @return 包含code、expireAt和gid的map
     */
    Map<String, Object> createInvite(Long uid, Long gid, int expireHours);

    /**
     * 根据邀请码获取邀请详情。
     *
     * @param code 邀请码
     * @return 包含群名、过期和使用信息的map
     * @throws me.aster.echochat.common.exception.BusinessException 如果邀请不存在
     */
    Map<String, Object> getInviteInfo(String code);
}
