package com.team.blog.friend.application;

import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 친구 그룹과 그룹 공개 대상(030, 강성찬 개인 확장). 그룹에는 수락된 친구만 넣을 수 있고, 친구를 끊으면 서로의 그룹에서 빠진다.
 * 그룹 공개 글은 글쓴이와, 그 글에 고른 그룹 중 하나에 든 사람만 읽는다.
 */
@Service
public class GroupService {

    public record Group(long id, String name, List<FriendQuery.Person> members) {
    }

    private final JdbcTemplate jdbc;
    private final FriendQuery friends;
    private final GroupProperties properties;

    public GroupService(JdbcTemplate jdbc, FriendQuery friends, GroupProperties properties) {
        this.jdbc = jdbc;
        this.friends = friends;
        this.properties = properties;
    }

    public List<Group> groups(long owner) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name FROM friend_group WHERE owner_id = ? ORDER BY created_at, id", owner);
        return rows.stream().map(r -> {
            long id = ((Number) r.get("id")).longValue();
            List<FriendQuery.Person> members = jdbc.query("""
                    SELECT m.id, m.handle, m.nickname, m.profile_image_url FROM group_member gm JOIN member m ON m.id = gm.member_id
                    WHERE gm.group_id = ? AND m.withdrawn_at IS NULL ORDER BY gm.added_at, m.id
                    """, (rs, n) -> new FriendQuery.Person(rs.getLong("id"), rs.getString("handle"), rs.getString("nickname"),
                    rs.getString("profile_image_url")), id);
            return new Group(id, (String) r.get("name"), members);
        }).toList();
    }

    public void create(long owner, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 20) {
            throw new PostContentException("GROUP_NAME_INVALID");
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM friend_group WHERE owner_id = ?", Integer.class, owner);
        if (count != null && count >= properties.maxGroups()) {
            throw new PostContentException("GROUP_LIMIT");
        }
        jdbc.update("INSERT INTO friend_group (owner_id, name) VALUES (?, ?) ON CONFLICT (owner_id, name) DO NOTHING", owner, name);
    }

    public void delete(long owner, long groupId) {
        requireOwn(owner, groupId);
        jdbc.update("DELETE FROM friend_group WHERE id = ?", groupId);
    }

    /** 친구만 넣을 수 있다. */
    public void add(long owner, long groupId, long memberId) {
        requireOwn(owner, groupId);
        if (!friends.areFriends(owner, memberId)) {
            throw new NotFoundException();
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM group_member WHERE group_id = ?", Integer.class, groupId);
        if (count != null && count >= properties.maxMembers()) {
            throw new PostContentException("GROUP_FULL");
        }
        jdbc.update("INSERT INTO group_member (group_id, member_id) VALUES (?, ?) ON CONFLICT DO NOTHING", groupId, memberId);
    }

    public void remove(long owner, long groupId, long memberId) {
        requireOwn(owner, groupId);
        jdbc.update("DELETE FROM group_member WHERE group_id = ? AND member_id = ?", groupId, memberId);
    }

    /** 그룹 공개 글이 보일 그룹을 바꾼다(글쓴이의 그룹만). */
    public void setPostGroups(long owner, long postId, List<Long> groupIds) {
        Integer own = jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                Integer.class, postId, owner);
        if (own == null || own == 0) {
            throw new NotFoundException();
        }
        jdbc.update("DELETE FROM post_group_visibility WHERE post_id = ?", postId);
        for (Long groupId : groupIds == null ? List.<Long>of() : groupIds) {
            jdbc.update("""
                    INSERT INTO post_group_visibility (post_id, group_id)
                    SELECT ?, id FROM friend_group WHERE id = ? AND owner_id = ? ON CONFLICT DO NOTHING
                    """, postId, groupId, owner);
        }
    }

    public List<Long> postGroups(long postId) {
        return jdbc.queryForList("SELECT group_id FROM post_group_visibility WHERE post_id = ?", Long.class, postId);
    }

    public boolean canSee(long postId, long viewer) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM post_group_visibility pg JOIN group_member gm ON gm.group_id = pg.group_id
                               WHERE pg.post_id = ? AND gm.member_id = ?)
                """, Boolean.class, postId, viewer));
    }

    /** 친구를 끊으면 서로의 그룹에서 뺀다. */
    public void removeBetween(long a, long b) {
        jdbc.update("""
                DELETE FROM group_member gm USING friend_group g
                WHERE gm.group_id = g.id AND ((g.owner_id = ? AND gm.member_id = ?) OR (g.owner_id = ? AND gm.member_id = ?))
                """, a, b, b, a);
    }

    /** 탈퇴 정리: 내 그룹과, 남의 그룹에 든 나를 지운다. */
    public void purgeWithdrawn(long memberId) {
        jdbc.update("DELETE FROM group_member WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM friend_group WHERE owner_id = ?", memberId);
    }

    private void requireOwn(long owner, long groupId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM friend_group WHERE id = ? AND owner_id = ?", Integer.class,
                groupId, owner);
        if (n == null || n == 0) {
            throw new NotFoundException();
        }
    }
}
