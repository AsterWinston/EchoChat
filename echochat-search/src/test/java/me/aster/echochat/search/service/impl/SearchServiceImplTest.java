package me.aster.echochat.search.service.impl;

import me.aster.echochat.common.entity.User;
import me.aster.echochat.search.client.GroupFeignClient;
import me.aster.echochat.search.client.MessageFeignClient;
import me.aster.echochat.search.client.UserFeignClient;
import me.aster.echochat.search.entity.GroupDocument;
import me.aster.echochat.search.entity.MessageDocument;
import me.aster.echochat.search.entity.UserDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl")
class SearchServiceImplTest {

    @Mock
    private ElasticsearchOperations operations;

    @Mock
    private UserFeignClient userFeignClient;

    @Mock
    private GroupFeignClient groupFeignClient;

    @Mock
    private MessageFeignClient messageFeignClient;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Nested
    @DisplayName("searchMessages")
    class SearchMessagesTests {

        private MessageDocument msgDoc;

        @BeforeEach
        void setUp() {
            msgDoc = new MessageDocument();
            msgDoc.setMsgId(1L);
            msgDoc.setFromUid(10L);
            msgDoc.setToId("20");
            msgDoc.setContent("hello world");
        }

        @Test
        @DisplayName("should return matching messages")
        @SuppressWarnings("unchecked")
        void shouldReturnMatchingMessages() {
            SearchHits<MessageDocument> searchHits = org.mockito.Mockito.mock(SearchHits.class);
            SearchHit<MessageDocument> searchHit = org.mockito.Mockito.mock(SearchHit.class);
            when(searchHit.getContent()).thenReturn(msgDoc);
            when(searchHits.stream()).thenReturn(Stream.of(searchHit));

            when(operations.search(any(CriteriaQuery.class), eq(MessageDocument.class))).thenReturn(searchHits);
            when(messageFeignClient.getDeletedMsgIds(anyMap())).thenReturn(Collections.emptyList());

            List<MessageDocument> results = searchService.searchMessages(10L, "hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isEqualTo(msgDoc);
        }

        @Test
        @DisplayName("should return empty list for blank keyword")
        void shouldReturnEmptyForBlankKeyword() {
            List<MessageDocument> results = searchService.searchMessages(10L, "   ");

            assertThat(results).isEmpty();
            verify(operations, never()).search(any(CriteriaQuery.class), any());
        }

        @Test
        @DisplayName("should return empty list for null keyword")
        void shouldReturnEmptyForNullKeyword() {
            List<MessageDocument> results = searchService.searchMessages(10L, null);

            assertThat(results).isEmpty();
            verify(operations, never()).search(any(CriteriaQuery.class), any());
        }

        @Test
        @DisplayName("should filter out deleted messages")
        @SuppressWarnings("unchecked")
        void shouldFilterDeletedMessages() {
            MessageDocument msgDoc2 = new MessageDocument();
            msgDoc2.setMsgId(2L);
            msgDoc2.setFromUid(10L);
            msgDoc2.setToId("20");
            msgDoc2.setContent("another message");

            SearchHits<MessageDocument> searchHits = org.mockito.Mockito.mock(SearchHits.class);
            SearchHit<MessageDocument> hit1 = org.mockito.Mockito.mock(SearchHit.class);
            SearchHit<MessageDocument> hit2 = org.mockito.Mockito.mock(SearchHit.class);
            when(hit1.getContent()).thenReturn(msgDoc);
            when(hit2.getContent()).thenReturn(msgDoc2);
            when(searchHits.stream()).thenReturn(Stream.of(hit1, hit2));

            when(operations.search(any(CriteriaQuery.class), eq(MessageDocument.class))).thenReturn(searchHits);
            when(messageFeignClient.getDeletedMsgIds(anyMap())).thenReturn(List.of(1L));

            List<MessageDocument> results = searchService.searchMessages(10L, "hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMsgId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("searchMessagesWithUser")
    class SearchMessagesWithUserTests {

        @Test
        @DisplayName("should return messages between two users")
        @SuppressWarnings("unchecked")
        void shouldReturnMessagesBetweenTwoUsers() {
            MessageDocument doc = new MessageDocument();
            doc.setMsgId(5L);
            doc.setFromUid(10L);
            doc.setToId("20");
            doc.setContent("direct message");

            SearchHits<MessageDocument> searchHits = org.mockito.Mockito.mock(SearchHits.class);
            SearchHit<MessageDocument> searchHit = org.mockito.Mockito.mock(SearchHit.class);
            when(searchHit.getContent()).thenReturn(doc);
            when(searchHits.stream()).thenReturn(Stream.of(searchHit));

            when(operations.search(any(CriteriaQuery.class), eq(MessageDocument.class))).thenReturn(searchHits);
            when(messageFeignClient.getDeletedMsgIds(anyMap())).thenReturn(Collections.emptyList());

            List<MessageDocument> results = searchService.searchMessagesWithUser(10L, 20L, "direct");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMsgId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return empty list for blank keyword")
        void shouldReturnEmptyForBlankKeywordInUserSearch() {
            List<MessageDocument> results = searchService.searchMessagesWithUser(10L, 20L, "");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchUsers")
    class SearchUsersTests {

        @Test
        @DisplayName("should return ES results when found")
        @SuppressWarnings("unchecked")
        void shouldReturnEsResultsWhenFound() {
            UserDocument userDoc = new UserDocument();
            userDoc.setUid(30L);
            userDoc.setNickname("Alice");
            userDoc.setEmail("alice@example.com");

            SearchHits<UserDocument> searchHits = org.mockito.Mockito.mock(SearchHits.class);
            SearchHit<UserDocument> searchHit = org.mockito.Mockito.mock(SearchHit.class);
            when(searchHit.getContent()).thenReturn(userDoc);
            when(searchHits.stream()).thenReturn(Stream.of(searchHit));

            when(operations.search(any(CriteriaQuery.class), eq(UserDocument.class))).thenReturn(searchHits);

            List<UserDocument> results = searchService.searchUsers("Alice");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getNickname()).isEqualTo("Alice");
            assertThat(results.get(0).getEmail()).isEqualTo("alice@example.com");
            verify(userFeignClient, never()).searchUsers(anyString());
        }

        @Test
        @DisplayName("should fallback to MySQL when ES returns empty")
        @SuppressWarnings("unchecked")
        void shouldFallbackToMysqlWhenEsEmpty() {
            SearchHits<UserDocument> emptyHits = org.mockito.Mockito.mock(SearchHits.class);
            when(emptyHits.stream()).thenReturn(Stream.empty());
            when(operations.search(any(CriteriaQuery.class), eq(UserDocument.class))).thenReturn(emptyHits);

            User mysqlUser = new User();
            mysqlUser.setUid(40L);
            mysqlUser.setNickname("Bob");
            mysqlUser.setEmail("bob@example.com");
            when(userFeignClient.searchUsers("Bob")).thenReturn(List.of(mysqlUser));

            List<UserDocument> results = searchService.searchUsers("Bob");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getNickname()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("should return empty list for blank keyword")
        void shouldReturnEmptyForBlankUserKeyword() {
            List<UserDocument> results = searchService.searchUsers("");

            assertThat(results).isEmpty();
            verify(operations, never()).search(any(CriteriaQuery.class), any());
        }
    }

    @Nested
    @DisplayName("searchGroups")
    class SearchGroupsTests {

        @Test
        @DisplayName("should return ES group results")
        @SuppressWarnings("unchecked")
        void shouldReturnEsGroupResults() {
            GroupDocument groupDoc = new GroupDocument();
            groupDoc.setGid(50L);
            groupDoc.setName("Developers");
            groupDoc.setOwnerUid(10L);

            SearchHits<GroupDocument> searchHits = org.mockito.Mockito.mock(SearchHits.class);
            SearchHit<GroupDocument> searchHit = org.mockito.Mockito.mock(SearchHit.class);
            when(searchHit.getContent()).thenReturn(groupDoc);
            when(searchHits.stream()).thenReturn(Stream.of(searchHit));

            when(operations.search(any(CriteriaQuery.class), eq(GroupDocument.class))).thenReturn(searchHits);

            List<GroupDocument> results = searchService.searchGroups("Dev");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Developers");
            verify(groupFeignClient, never()).searchGroups(anyString());
        }

        @Test
        @DisplayName("should fallback to MySQL when ES returns empty for groups")
        @SuppressWarnings("unchecked")
        void shouldFallbackToMysqlForGroups() {
            SearchHits<GroupDocument> emptyHits = org.mockito.Mockito.mock(SearchHits.class);
            when(emptyHits.stream()).thenReturn(Stream.empty());
            when(operations.search(any(CriteriaQuery.class), eq(GroupDocument.class))).thenReturn(emptyHits);

            GroupDocument fallbackGroup = new GroupDocument();
            fallbackGroup.setGid(60L);
            fallbackGroup.setName("Design Team");
            when(groupFeignClient.searchGroups("Design")).thenReturn(List.of(fallbackGroup));

            List<GroupDocument> results = searchService.searchGroups("Design");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Design Team");
        }

        @Test
        @DisplayName("should return empty list for null keyword")
        void shouldReturnEmptyForNullGroupKeyword() {
            List<GroupDocument> results = searchService.searchGroups(null);

            assertThat(results).isEmpty();
            verify(operations, never()).search(any(CriteriaQuery.class), any());
        }
    }
}
