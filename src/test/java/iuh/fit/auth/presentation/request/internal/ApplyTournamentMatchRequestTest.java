package iuh.fit.auth.presentation.request.internal;

import iuh.fit.auth.domain.enums.SportType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyTournamentMatchRequestTest {

    /** Đúng JSON mà club-service gửi (SportRatingPort.TournamentMatch đặt tên là fixtureId). */
    @Test
    void acceptsTheFieldNameClubServiceSends() {
        ObjectMapper mapper = JsonMapper.builder().build();
        UUID fixtureId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        String json = """
                {"fixtureId":"%s","tournamentId":"%s","sportType":"FOOTBALL","title":"Chung kết",
                 "first":{"name":"A","score":2,"playerIds":["%s"]},
                 "second":{"name":"B","score":1,"playerIds":["%s"]}}
                """.formatted(fixtureId, UUID.randomUUID(), player, UUID.randomUUID());

        ApplyTournamentMatchRequest request = mapper.readValue(json, ApplyTournamentMatchRequest.class);

        assertThat(request.matchId()).isEqualTo(fixtureId);
        assertThat(request.sportType()).isEqualTo(SportType.FOOTBALL);
        assertThat(request.first().playerIds()).containsExactly(player);
    }
}
