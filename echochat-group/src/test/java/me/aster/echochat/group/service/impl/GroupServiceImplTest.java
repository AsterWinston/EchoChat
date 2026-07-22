package me.aster.echochat.group.service.impl;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.entity.GroupRole;
import me.aster.echochat.group.mapper.GroupInfoMapper;
import me.aster.echochat.group.mapper.GroupMemberMapper;
import me.aster.echochat.group.mq.GroupIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GroupServiceImpl} verifying group CRUD,
 * ownership transfer, dissolve, mute-all, and member queries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupServiceImpl")
class GroupServiceImplTest {

    @Mock
    private GroupInfoMapper groupInfoMapper;

    @Mock
    private GroupMemberMapper groupMemberMapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private GroupIndexService groupIndexService;

    @InjectMocks
    private GroupServiceImpl groupService;

    @Nested
    @DisplayName("createGroup")
    class CreateGroup {

        @Test
        @DisplayName("should create group and assign owner role on success")
        void shouldCreateGroupAndAssignOwnerRole() {
            when(idGenerator.nextId()).thenReturn(1001L);
            when(groupInfoMapper.insert(any(GroupInfo.class))).thenReturn(1);
            when(groupMemberMapper.insert(any(GroupMember.class))).thenReturn(1);

            GroupInfo result = groupService.createGroup(10L, "Test Group");

            assertThat(result).isNotNull();
            assertThat(result.getGid()).isEqualTo(1001L);
            assertThat(result.getName()).isEqualTo("Test Group");
            assertThat(result.getOwnerUid()).isEqualTo(10L);
            assertThat(result.getSlowModeInterval()).isZero();
            verify(groupInfoMapper).insert(any(GroupInfo.class));
            verify(groupMemberMapper).insert(any(GroupMember.class));
            verify(groupIndexService).syncGroupToEs(result);
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when name is null")
        void shouldThrowWhenNameIsNull() {
            assertThatThrownBy(() -> groupService.createGroup(10L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group name cannot be empty");
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when name is blank")
        void shouldThrowWhenNameIsBlank() {
            assertThatThrownBy(() -> groupService.createGroup(10L, "   "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group name cannot be empty");
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when name exceeds 128 characters")
        void shouldThrowWhenNameExceeds128Characters() {
            String longName = "a".repeat(129);

            assertThatThrownBy(() -> groupService.createGroup(10L, longName))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group name cannot exceed 128 characters");
        }
    }

    @Nested
    @DisplayName("getGroupInfo")
    class GetGroupInfo {

        private Long gid;
        private GroupInfo group;

        @BeforeEach
        void setUp() {
            gid = 1001L;
            group = new GroupInfo();
            group.setGid(gid);
            group.setName("Test Group");
            group.setOwnerUid(10L);
        }

        @Test
        @DisplayName("should return group info when group exists")
        void shouldReturnGroupInfoWhenExists() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);

            GroupInfo result = groupService.getGroupInfo(gid);

            assertThat(result).isNotNull();
            assertThat(result.getGid()).isEqualTo(gid);
            assertThat(result.getName()).isEqualTo("Test Group");
            verify(groupInfoMapper).selectById(gid);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when group does not exist")
        void shouldThrowNotFoundWhenGroupMissing() {
            when(groupInfoMapper.selectById(gid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.getGroupInfo(gid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group not found");
        }
    }

    @Nested
    @DisplayName("updateGroup")
    class UpdateGroup {

        private Long uid;
        private Long gid;
        private GroupMember member;
        private GroupInfo group;

        @BeforeEach
        void setUp() {
            uid = 10L;
            gid = 1001L;
            member = new GroupMember();
            member.setId(1L);
            member.setGid(gid);
            member.setUid(uid);
            member.setRole(GroupRole.OWNER.name().toLowerCase());

            group = new GroupInfo();
            group.setGid(gid);
            group.setName("Old Name");
            group.setOwnerUid(uid);
        }

        @Test
        @DisplayName("should update group fields when operator is owner")
        void shouldUpdateGroupByOwner() {
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupInfoMapper.updateById(any(GroupInfo.class))).thenReturn(1);

            Map<String, Object> updates = Map.of("name", "New Name");
            GroupInfo result = groupService.updateGroup(uid, gid, updates);

            assertThat(result.getName()).isEqualTo("New Name");
            verify(groupInfoMapper).updateById(any(GroupInfo.class));
            verify(groupIndexService).syncGroupToEs(result);
        }

        @Test
        @DisplayName("should update group fields when operator is admin")
        void shouldUpdateGroupByAdmin() {
            member.setRole(GroupRole.ADMIN.name().toLowerCase());
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupInfoMapper.updateById(any(GroupInfo.class))).thenReturn(1);

            Map<String, Object> updates = Map.of("announcement", "Welcome!");
            GroupInfo result = groupService.updateGroup(uid, gid, updates);

            assertThat(result.getAnnouncement()).isEqualTo("Welcome!");
            verify(groupInfoMapper).updateById(any(GroupInfo.class));
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is not a member")
        void shouldThrowWhenNotMember() {
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.updateGroup(uid, gid, Map.of("name", "X")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Not a member of this group");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is a regular member")
        void shouldThrowWhenNoPermission() {
            member.setRole(GroupRole.MEMBER.name().toLowerCase());
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);

            assertThatThrownBy(() -> groupService.updateGroup(uid, gid, Map.of("name", "X")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only owner and admins can edit group info");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when group does not exist")
        void shouldThrowNotFoundWhenGroupMissing() {
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.selectById(gid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.updateGroup(uid, gid, Map.of("name", "X")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group not found");
        }
    }

    @Nested
    @DisplayName("dissolveGroup")
    class DissolveGroup {

        private Long uid;
        private Long gid;
        private GroupInfo group;

        @BeforeEach
        void setUp() {
            uid = 10L;
            gid = 1001L;
            group = new GroupInfo();
            group.setGid(gid);
            group.setName("Test Group");
            group.setOwnerUid(uid);
        }

        @Test
        @DisplayName("should dissolve group when operator is owner")
        void shouldDissolveGroupSuccessfully() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.delete(any())).thenReturn(1);
            when(groupInfoMapper.deleteById(gid)).thenReturn(1);

            groupService.dissolveGroup(uid, gid);

            verify(groupMemberMapper).delete(any());
            verify(groupInfoMapper).deleteById(gid);
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is not the owner")
        void shouldThrowWhenNotOwner() {
            Long otherUid = 99L;
            when(groupInfoMapper.selectById(gid)).thenReturn(group);

            assertThatThrownBy(() -> groupService.dissolveGroup(otherUid, gid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only owner can dissolve the group");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when group does not exist")
        void shouldThrowNotFoundWhenGroupMissing() {
            when(groupInfoMapper.selectById(gid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.dissolveGroup(uid, gid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group not found");
        }
    }

    @Nested
    @DisplayName("transferOwner")
    class TransferOwner {

        private Long uid;
        private Long gid;
        private Long newOwnerUid;
        private GroupInfo group;
        private GroupMember oldOwner;
        private GroupMember newOwner;

        @BeforeEach
        void setUp() {
            uid = 10L;
            gid = 1001L;
            newOwnerUid = 20L;

            group = new GroupInfo();
            group.setGid(gid);
            group.setName("Test Group");
            group.setOwnerUid(uid);

            oldOwner = new GroupMember();
            oldOwner.setId(1L);
            oldOwner.setGid(gid);
            oldOwner.setUid(uid);
            oldOwner.setRole(GroupRole.OWNER.name().toLowerCase());

            newOwner = new GroupMember();
            newOwner.setId(2L);
            newOwner.setGid(gid);
            newOwner.setUid(newOwnerUid);
            newOwner.setRole(GroupRole.MEMBER.name().toLowerCase());
        }

        @Test
        @DisplayName("should transfer ownership and demote old owner to admin")
        void shouldTransferOwnershipSuccessfully() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, newOwnerUid)).thenReturn(newOwner);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(oldOwner);
            when(groupMemberMapper.updateById(oldOwner)).thenReturn(1);
            when(groupMemberMapper.updateById(newOwner)).thenReturn(1);
            when(groupInfoMapper.updateById(group)).thenReturn(1);

            groupService.transferOwner(uid, gid, newOwnerUid);

            assertThat(group.getOwnerUid()).isEqualTo(newOwnerUid);
            assertThat(oldOwner.getRole()).isEqualTo(GroupRole.ADMIN.name().toLowerCase());
            assertThat(newOwner.getRole()).isEqualTo(GroupRole.OWNER.name().toLowerCase());
            verify(groupMemberMapper).updateById(oldOwner);
            verify(groupMemberMapper).updateById(newOwner);
            verify(groupInfoMapper).updateById(group);
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is not the owner")
        void shouldThrowWhenNotOwner() {
            Long otherUid = 99L;
            when(groupInfoMapper.selectById(gid)).thenReturn(group);

            assertThatThrownBy(() -> groupService.transferOwner(otherUid, gid, newOwnerUid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only owner can transfer");
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when target user is not in the group")
        void shouldThrowWhenTargetNotInGroup() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, newOwnerUid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.transferOwner(uid, gid, newOwnerUid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Target user is not in the group");
        }
    }

    @Nested
    @DisplayName("getJoinedGroups")
    class GetJoinedGroups {

        @Test
        @DisplayName("should return groups where user is a member")
        void shouldReturnJoinedGroups() {
            GroupInfo g1 = new GroupInfo();
            g1.setGid(1001L);
            g1.setName("Group A");
            when(groupInfoMapper.findByMemberUid(10L)).thenReturn(List.of(g1));

            List<GroupInfo> result = groupService.getJoinedGroups(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Group A");
            verify(groupInfoMapper).findByMemberUid(10L);
        }

        @Test
        @DisplayName("should return empty list when user has joined no groups")
        void shouldReturnEmptyListWhenNoGroups() {
            when(groupInfoMapper.findByMemberUid(10L)).thenReturn(List.of());

            List<GroupInfo> result = groupService.getJoinedGroups(10L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("muteAll")
    class MuteAll {

        private Long uid;
        private Long gid;
        private GroupInfo group;
        private GroupMember member;

        @BeforeEach
        void setUp() {
            uid = 10L;
            gid = 1001L;

            group = new GroupInfo();
            group.setGid(gid);
            group.setName("Test Group");
            group.setOwnerUid(uid);
            group.setMuteAll(0);

            member = new GroupMember();
            member.setId(1L);
            member.setGid(gid);
            member.setUid(uid);
            member.setRole(GroupRole.OWNER.name().toLowerCase());
        }

        @Test
        @DisplayName("should enable mute-all when called with mute=true by owner")
        void shouldMuteAllSuccessfully() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.updateById(group)).thenReturn(1);

            groupService.muteAll(uid, gid, true);

            assertThat(group.getMuteAll()).isEqualTo(1);
            verify(groupInfoMapper).updateById(group);
        }

        @Test
        @DisplayName("should disable mute-all when called with mute=false by owner")
        void shouldUnmuteAllSuccessfully() {
            group.setMuteAll(1);
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.updateById(group)).thenReturn(1);

            groupService.muteAll(uid, gid, false);

            assertThat(group.getMuteAll()).isEqualTo(0);
            verify(groupInfoMapper).updateById(group);
        }

        @Test
        @DisplayName("should enable mute-all when called by admin")
        void shouldMuteAllByAdmin() {
            member.setRole(GroupRole.ADMIN.name().toLowerCase());
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);
            when(groupInfoMapper.updateById(group)).thenReturn(1);

            groupService.muteAll(uid, gid, true);

            assertThat(group.getMuteAll()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is not a member")
        void shouldThrowWhenNotMember() {
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.muteAll(uid, gid, true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Not a member of this group");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when operator is a regular member")
        void shouldThrowWhenNoPermission() {
            member.setRole(GroupRole.MEMBER.name().toLowerCase());
            when(groupInfoMapper.selectById(gid)).thenReturn(group);
            when(groupMemberMapper.findByGidAndUid(gid, uid)).thenReturn(member);

            assertThatThrownBy(() -> groupService.muteAll(uid, gid, true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only owner and admins can manage mute-all");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when group does not exist")
        void shouldThrowNotFoundWhenGroupMissing() {
            when(groupInfoMapper.selectById(gid)).thenReturn(null);

            assertThatThrownBy(() -> groupService.muteAll(uid, gid, true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Group not found");
        }
    }

    @Nested
    @DisplayName("searchGroups")
    class SearchGroups {

        @Test
        @DisplayName("should return empty list when keyword is null")
        void shouldReturnEmptyWhenKeywordNull() {
            List<GroupInfo> result = groupService.searchGroups(null);

            assertThat(result).isEmpty();
            verify(groupInfoMapper, never()).searchByName(any());
        }

        @Test
        @DisplayName("should return empty list when keyword is blank")
        void shouldReturnEmptyWhenKeywordBlank() {
            List<GroupInfo> result = groupService.searchGroups("   ");

            assertThat(result).isEmpty();
            verify(groupInfoMapper, never()).searchByName(any());
        }

        @Test
        @DisplayName("should return matching groups for valid keyword")
        void shouldReturnMatchingGroups() {
            GroupInfo g = new GroupInfo();
            g.setGid(1001L);
            g.setName("Test Group");
            when(groupInfoMapper.searchByName("Test")).thenReturn(List.of(g));

            List<GroupInfo> result = groupService.searchGroups("Test");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Test Group");
            verify(groupInfoMapper).searchByName("Test");
        }
    }
}
