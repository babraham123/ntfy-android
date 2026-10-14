package io.heckel.ntfy.util

import io.heckel.ntfy.db.User
import io.heckel.ntfy.util.EnrollLink.Decision
import io.heckel.ntfy.util.EnrollLink.RefuseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollLinkTest {
    private val phil = User("https://ntfy.example.com", "phil", "")
    private val philWithToken = User("https://ntfy.example.com", "phil", "", "tk_abc")

    private fun parse(link: String): EnrollLink = EnrollLink.parse(link) ?: throw AssertionError("Link $link did not parse")
    private fun decide(link: String, savedUser: User? = null, hasCustomAuthHeader: Boolean = false) =
        EnrollLink.decide(parse(link), savedUser, hasCustomAuthHeader)

    @Test
    fun `parse full link`() {
        val link = parse("ntfy://ntfy.example.com/alerts?user=phil&code=abc123&display=My%20Alerts")
        assertEquals("https://ntfy.example.com", link.baseUrl)
        assertEquals("alerts", link.topic)
        assertEquals("phil", link.user)
        assertEquals("abc123", link.code)
        assertEquals("My Alerts", link.displayName)
        assertTrue(link.secure)
    }

    @Test
    fun `parse base url from port and secure`() {
        assertEquals("https://ntfy.example.com", parse("ntfy://ntfy.example.com:443/t").baseUrl)
        assertEquals("https://ntfy.example.com:8443", parse("ntfy://ntfy.example.com:8443/t").baseUrl)
        assertEquals("http://10.0.2.2:8080", parse("ntfy://10.0.2.2:8080/t?secure=false").baseUrl)
        assertEquals("http://10.0.2.2", parse("ntfy://10.0.2.2:80/t?secure=false").baseUrl)
        assertFalse(parse("ntfy://h/t?secure=0").secure)
        assertTrue(parse("ntfy://h/t?secure=true").secure)
    }

    @Test
    fun `parse without code or user`() {
        val link = parse("ntfy://ntfy.example.com/alerts")
        assertNull(link.user)
        assertNull(link.code)
        assertNull(link.displayName)
        assertNull(parse("ntfy://ntfy.example.com/alerts?user=&code=").code)
    }

    @Test
    fun `parse keeps plus literally`() {
        assertEquals("a+b", parse("ntfy://h/t?code=a+b").code)
    }

    @Test
    fun `parse rejects invalid links`() {
        assertNull(EnrollLink.parse("https://ntfy.example.com/alerts"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com/"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com/a/b"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com/bad.topic"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com/${"a".repeat(65)}"))
        assertNull(EnrollLink.parse("ntfy://ntfy.example.com/t?code=%zz"))
        assertNull(EnrollLink.parse("not a link"))
    }

    @Test
    fun `code with secure=false is refused`() {
        assertEquals(Decision.Refuse(RefuseReason.InsecureCode), decide("ntfy://h/t?user=phil&code=abc&secure=false"))
        assertEquals(Decision.Refuse(RefuseReason.InsecureCode), decide("ntfy://h/t?user=phil&code=abc&secure=false", savedUser = phil))
    }

    @Test
    fun `no saved user with code redeems`() {
        assertEquals(Decision.Redeem, decide("ntfy://h/t?user=phil&code=abc"))
        assertEquals(Decision.Redeem, decide("ntfy://h/t?code=abc"))
    }

    @Test
    fun `no saved user without code subscribes`() {
        assertEquals(Decision.Subscribe, decide("ntfy://h/t"))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t?user=phil"))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t?secure=false"))
    }

    @Test
    fun `matching saved user subscribes and ignores code`() {
        assertEquals(Decision.Subscribe, decide("ntfy://h/t?user=phil&code=abc", savedUser = phil))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t?user=phil&code=abc", savedUser = philWithToken))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t?code=abc", savedUser = phil))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t", savedUser = phil))
    }

    @Test
    fun `other saved user is refused`() {
        assertEquals(Decision.Refuse(RefuseReason.OtherUser), decide("ntfy://h/t?user=bob&code=abc", savedUser = phil))
        assertEquals(Decision.Refuse(RefuseReason.OtherUser), decide("ntfy://h/t?user=bob", savedUser = phil))
    }

    @Test
    fun `custom authorization header with code is refused`() {
        assertEquals(Decision.Refuse(RefuseReason.AuthorizationHeader), decide("ntfy://h/t?user=phil&code=abc", hasCustomAuthHeader = true))
        assertEquals(Decision.Subscribe, decide("ntfy://h/t", hasCustomAuthHeader = true))
    }
}
