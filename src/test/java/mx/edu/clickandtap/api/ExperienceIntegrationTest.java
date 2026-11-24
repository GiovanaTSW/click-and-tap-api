package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceIntegrationTest extends IntegrationTestBase {

    // ---------- create and read ----------

    @Test
    void creatingAnExperienceGeneratesASlugAndNumbersTheLinks() throws Exception {
        Customer owner = registerCustomer();
        String title = unique("Mi Perfil");

        MvcResult result = call(POST, "/api/experiences", owner.token(), experienceJson(title,
                link("Web", "https://example.com"), link("Correo", "mailto:hola@example.com")));

        assertThat(status(result)).isEqualTo(201);
        assertThat(str(result, "$.slug")).isEqualTo(title.toLowerCase().replace(" ", "-"));
        assertThat(str(result, "$.type")).isEqualTo("PROFILE");
        assertThat(bool(result, "$.active")).isTrue();
        assertThat(integer(result, "$.links[0].position")).isZero();
        assertThat(integer(result, "$.links[1].position")).isEqualTo(1);
        assertThat(body(result)).as("owner id is internal").doesNotContain("userId");
    }

    @Test
    void twoExperiencesWithTheSameTitleGetDifferentSlugs() throws Exception {
        Customer owner = registerCustomer();
        String title = unique("Cafe Luna");

        MvcResult first = call(POST, "/api/experiences", owner.token(), experienceJson(title));
        MvcResult second = call(POST, "/api/experiences", owner.token(), experienceJson(title));

        assertThat(status(second)).isEqualTo(201);
        assertThat(str(second, "$.slug")).isNotEqualTo(str(first, "$.slug")).matches(".+-[a-z0-9]{4}");
    }

    @Test
    void aRequestedSlugIsCleanedUp() throws Exception {
        Customer owner = registerCustomer();
        String slug = unique("Mi Slug Ñandú");

        MvcResult result = call(POST, "/api/experiences", owner.token(), """
                {"type":"DIGITAL_MENU","title":"Menu","slug":"%s"}""".formatted(slug));

        assertThat(status(result)).isEqualTo(201);
        assertThat(str(result, "$.slug")).matches("[a-z0-9-]+").startsWith("mi-slug-nandu");
    }

    @Test
    void theOwnerListsOnlyTheirOwnExperiences() throws Exception {
        Customer me = registerCustomer();
        Customer other = registerCustomer();
        call(POST, "/api/experiences", me.token(), experienceJson(unique("mia")));
        call(POST, "/api/experiences", other.token(), experienceJson(unique("ajena")));

        MvcResult mine = call(GET, "/api/experiences", me.token(), null);

        assertThat(status(mine)).isEqualTo(200);
        List<Object> items = at(mine, "$");
        assertThat(items).hasSize(1);
        assertThat(body(mine)).contains("mia").doesNotContain("ajena");
    }

    // ---------- validation ----------

    @Test
    void dangerousLinkUrlsAreRejected() throws Exception {
        Customer owner = registerCustomer();

        for (String url : List.of("javascript:alert(1)", "data:text/html,x", "//evil.com", "ftp://x.com", "file:///etc/passwd")) {
            MvcResult result = call(POST, "/api/experiences", owner.token(),
                    experienceJson(unique("peligro"), link("Click", url)));

            assertThat(status(result)).as(url).isEqualTo(400);
        }
    }

    @Test
    void invalidExperienceDataIsRejected() throws Exception {
        Customer owner = registerCustomer();

        assertThat(status(call(POST, "/api/experiences", owner.token(),
                "{\"type\":\"PROFILE\",\"title\":\"  \"}"))).as("blank title").isEqualTo(400);
        assertThat(status(call(POST, "/api/experiences", owner.token(),
                "{\"title\":\"Sin tipo\"}"))).as("no type").isEqualTo(400);
        assertThat(status(call(POST, "/api/experiences", owner.token(),
                "{\"type\":\"SPACESHIP\",\"title\":\"x\"}"))).as("unknown type").isEqualTo(400);
        assertThat(status(call(POST, "/api/experiences", owner.token(),
                experienceJson("x".repeat(200))))).as("title too long").isEqualTo(400);

        String[] links = new String[21];
        for (int i = 0; i < links.length; i++) links[i] = link("L" + i, "https://example.com/" + i);
        assertThat(status(call(POST, "/api/experiences", owner.token(),
                experienceJson(unique("muchos"), links)))).as("more than 20 links").isEqualTo(400);
    }

    // ---------- ownership ----------

    @Test
    void aStrangerCannotReadChangeOrDeleteSomeoneElsesExperience() throws Exception {
        Customer owner = registerCustomer();
        Customer stranger = registerCustomer();
        long id = lng(call(POST, "/api/experiences", owner.token(), experienceJson(unique("ajena"))), "$.id");

        assertThat(status(call(GET, "/api/experiences/" + id, stranger.token(), null))).isEqualTo(403);
        assertThat(status(call(PUT, "/api/experiences/" + id, stranger.token(), updateJson("Robado", true)))).isEqualTo(403);
        assertThat(status(call(DELETE, "/api/experiences/" + id, stranger.token(), null))).isEqualTo(403);
        assertThat(status(call(GET, "/api/experiences/" + id, owner.token(), null))).as("still there").isEqualTo(200);
        assertThat(status(call(GET, "/api/experiences/" + id, adminToken(), null))).as("admin can see it").isEqualTo(200);
    }

    // ---------- editing ----------

    @Test
    void updatingChangesTheContentButNeverTheSlug() throws Exception {
        Customer owner = registerCustomer();
        MvcResult created = call(POST, "/api/experiences", owner.token(),
                experienceJson(unique("Impreso"), link("Viejo", "https://old.example.com")));
        long id = lng(created, "$.id");
        String slug = str(created, "$.slug");

        MvcResult updated = call(PUT, "/api/experiences/" + id, owner.token(), """
                {"title":"Titulo nuevo","slug":"otro-slug","active":true,
                 "links":[{"label":"A","url":"https://a.example.com"},{"label":"B","url":"https://b.example.com"}]}""");

        assertThat(status(updated)).isEqualTo(200);
        assertThat(str(updated, "$.slug")).as("printed on a physical tag").isEqualTo(slug);
        assertThat(str(updated, "$.title")).isEqualTo("Titulo nuevo");
        List<Object> links = at(updated, "$.links");
        assertThat(links).hasSize(2);
        assertThat(str(updated, "$.links[0].label")).isEqualTo("A");
        assertThat(integer(updated, "$.links[1].position")).isEqualTo(1);
    }

    @Test
    void putMustSayWhetherTheExperienceIsActive() throws Exception {
        Customer owner = registerCustomer();
        long id = lng(call(POST, "/api/experiences", owner.token(), experienceJson(unique("sin-active"))), "$.id");

        MvcResult result = call(PUT, "/api/experiences/" + id, owner.token(), "{\"title\":\"x\"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(str(result, "$.message")).contains("active");
    }

    // ---------- the public page ----------

    @Test
    void thePublicPageIsOpenAndHidesInternalData() throws Exception {
        Customer owner = registerCustomer();
        MvcResult created = call(POST, "/api/experiences", owner.token(), experienceJson(unique("publica"),
                link("Web", "https://example.com")));
        String slug = str(created, "$.slug");

        MvcResult page = call(GET, "/api/public/experiences/" + slug, null, null);

        assertThat(status(page)).isEqualTo(200);
        assertThat(str(page, "$.slug")).isEqualTo(slug);
        assertThat(str(page, "$.links[0].url")).isEqualTo("https://example.com");
        assertThat(body(page)).doesNotContain("userId").doesNotContain("\"id\"").doesNotContain("orderItemId");
    }

    @Test
    void thePublicPageOnlyShowsActiveLinks() throws Exception {
        Customer owner = registerCustomer();
        MvcResult created = call(POST, "/api/experiences", owner.token(), """
                {"type":"PROFILE","title":"%s","links":[
                  {"label":"Visible","url":"https://visible.example.com"},
                  {"label":"Oculto","url":"https://hidden.example.com","active":false}]}""".formatted(unique("links")));

        MvcResult page = call(GET, "/api/public/experiences/" + str(created, "$.slug"), null, null);

        List<Object> links = at(page, "$.links");
        assertThat(links).hasSize(1);
        assertThat(str(page, "$.links[0].label")).isEqualTo("Visible");
    }

    @Test
    void aDeactivatedExperienceDisappearsFromThePublicPage() throws Exception {
        Customer owner = registerCustomer();
        MvcResult created = call(POST, "/api/experiences", owner.token(), experienceJson(unique("pausada")));
        long id = lng(created, "$.id");
        String slug = str(created, "$.slug");
        assertThat(status(call(GET, "/api/public/experiences/" + slug, null, null))).isEqualTo(200);

        call(PUT, "/api/experiences/" + id, owner.token(), updateJson("Pausada", false));

        assertThat(status(call(GET, "/api/public/experiences/" + slug, null, null))).isEqualTo(404);
        assertThat(status(call(GET, "/api/experiences/" + id, owner.token(), null))).as("owner still sees it").isEqualTo(200);
    }

    @Test
    void deletingAnExperienceRemovesItEverywhere() throws Exception {
        Customer owner = registerCustomer();
        MvcResult created = call(POST, "/api/experiences", owner.token(),
                experienceJson(unique("borrable"), link("Web", "https://example.com")));
        long id = lng(created, "$.id");
        String slug = str(created, "$.slug");

        assertThat(status(call(DELETE, "/api/experiences/" + id, owner.token(), null))).isEqualTo(204);

        assertThat(status(call(GET, "/api/experiences/" + id, owner.token(), null))).isEqualTo(404);
        assertThat(status(call(GET, "/api/public/experiences/" + slug, null, null))).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from experience_links where experience_id = ?",
                Integer.class, id)).as("links are deleted with it").isZero();
    }

    // ---------- helpers ----------

    private static String link(String label, String url) {
        return "{\"label\":\"%s\",\"url\":\"%s\"}".formatted(label, url);
    }

    private static String experienceJson(String title, String... links) {
        return "{\"type\":\"PROFILE\",\"title\":\"%s\",\"links\":[%s]}".formatted(title, String.join(",", links));
    }

    private static String updateJson(String title, boolean active) {
        return "{\"title\":\"%s\",\"active\":%b,\"links\":[]}".formatted(title, active);
    }
}
