package com.example.jarvis.ui.main

import com.example.jarvis.commands.CommandParser
import com.example.jarvis.commands.JarvisCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `open chrome`() {
        val cmd = CommandParser.parse("open chrome")
        assertTrue(cmd is JarvisCommand.OpenApp)
        assertEquals("chrome", (cmd as JarvisCommand.OpenApp).appNameRaw)
    }

    @Test
    fun `open insta alias`() {
        val cmd = CommandParser.parse("open insta")
        assertTrue(cmd is JarvisCommand.OpenApp)
        assertEquals("instagram", (cmd as JarvisCommand.OpenApp).appNameRaw)
    }

    @Test
    fun `direct app name without open prefix`() {
        val cmd = CommandParser.parse("insta")
        assertTrue(cmd is JarvisCommand.OpenApp)
        assertEquals("instagram", (cmd as JarvisCommand.OpenApp).appNameRaw)
    }

    @Test
    fun `wake word prefix stripping`() {
        val cmd = CommandParser.parse("hey jarvis open chrome")
        assertTrue(cmd is JarvisCommand.OpenApp)
        assertEquals("chrome", (cmd as JarvisCommand.OpenApp).appNameRaw)

        val blueCmd = CommandParser.parse("hey blue open chrome")
        assertTrue(blueCmd is JarvisCommand.OpenApp)
        assertEquals("chrome", (blueCmd as JarvisCommand.OpenApp).appNameRaw)

        val justBlueCmd = CommandParser.parse("blue open spotify")
        assertTrue(justBlueCmd is JarvisCommand.OpenApp)
        assertEquals("spotify", (justBlueCmd as JarvisCommand.OpenApp).appNameRaw)
    }

    @Test
    fun `wake word with flashlight`() {
        val cmd = CommandParser.parse("hey jarvis flashlight on")
        assertEquals(JarvisCommand.FlashlightOn, cmd)

        val blueCmd = CommandParser.parse("hey blue flashlight on")
        assertEquals(JarvisCommand.FlashlightOn, blueCmd)
    }

    @Test
    fun `battery status query`() {
        val cmd = CommandParser.parse("what is my battery")
        assertEquals(JarvisCommand.GetBattery, cmd)
    }

    @Test
    fun `media playback controls`() {
        assertEquals(JarvisCommand.MediaPlayPause, CommandParser.parse("play music"))
        assertEquals(JarvisCommand.MediaPlayPause, CommandParser.parse("pause"))
        assertEquals(JarvisCommand.MediaNext, CommandParser.parse("next song"))
        assertEquals(JarvisCommand.MediaPrevious, CommandParser.parse("previous track"))
    }

    @Test
    fun `reels navigation and like`() {
        assertEquals(JarvisCommand.NextReel, CommandParser.parse("next reel"))
        assertEquals(JarvisCommand.NextReel, CommandParser.parse("next video"))
        assertEquals(JarvisCommand.PreviousReel, CommandParser.parse("previous reel"))
        assertEquals(JarvisCommand.LikeReel, CommandParser.parse("like reel"))
    }

    @Test
    fun `send sms message`() {
        val cmd = CommandParser.parse("send text to alex saying hello there")
        assertTrue(cmd is JarvisCommand.SendSms)
        assertEquals("alex", (cmd as JarvisCommand.SendSms).contact)
        assertEquals("hello there", (cmd as JarvisCommand.SendSms).message)
    }

    @Test
    fun `timer command`() {
        val cmd = CommandParser.parse("set timer for 5 minutes")
        assertTrue(cmd is JarvisCommand.SetTimer)
        assertEquals(300, (cmd as JarvisCommand.SetTimer).seconds)
    }

    @Test
    fun `night mode routine`() {
        val cmd = CommandParser.parse("night mode")
        assertEquals(JarvisCommand.NightMode, cmd)
    }

    @Test
    fun `volume up`() {
        assertEquals(JarvisCommand.VolumeUp, CommandParser.parse("volume up"))
    }

    @Test
    fun `flashlight on`() {
        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("flashlight on"))
    }

    @Test
    fun `flashlight toggle`() {
        assertEquals(JarvisCommand.FlashlightToggle, CommandParser.parse("toggle flashlight"))
    }

    @Test
    fun `go back`() {
        assertEquals(JarvisCommand.GoBack, CommandParser.parse("go back"))
    }

    @Test
    fun `set volume`() {
        val cmd = CommandParser.parse("set volume to 8")
        assertTrue(cmd is JarvisCommand.SetVolume)
        assertEquals(8, (cmd as JarvisCommand.SetVolume).level)
    }

    @Test
    fun `web search`() {
        val cmd = CommandParser.parse("search for the weather")
        assertTrue(cmd is JarvisCommand.WebSearch)
        assertEquals("the weather", (cmd as JarvisCommand.WebSearch).query)
    }

    @Test
    fun `type text`() {
        val cmd = CommandParser.parse("type hello world")
        assertTrue(cmd is JarvisCommand.TypeText)
        assertEquals("hello world", (cmd as JarvisCommand.TypeText).text)
    }

    @Test
    fun `call contact`() {
        val cmd = CommandParser.parse("call john")
        assertTrue(cmd is JarvisCommand.CallContact)
        assertEquals("john", (cmd as JarvisCommand.CallContact).name)
    }

    @Test
    fun `open site in browser`() {
        val cmd = CommandParser.parse("open youtube on brave")
        assertTrue(cmd is JarvisCommand.OpenSiteInBrowser)
        assertEquals("youtube", (cmd as JarvisCommand.OpenSiteInBrowser).siteOrQuery)
        assertEquals("brave", (cmd as JarvisCommand.OpenSiteInBrowser).browserName)
    }

    @Test
    fun `call contact on speaker`() {
        val cmd = CommandParser.parse("call teja on speaker")
        assertTrue(cmd is JarvisCommand.CallContact)
        assertEquals("teja", (cmd as JarvisCommand.CallContact).name)
        assertTrue((cmd as JarvisCommand.CallContact).onSpeaker)
    }

    @Test
    fun `whatsapp call`() {
        val cmd = CommandParser.parse("call teja on whatsapp")
        assertTrue(cmd is JarvisCommand.WhatsAppCall)
        assertEquals("teja", (cmd as JarvisCommand.WhatsAppCall).contactName)
    }

    @Test
    fun `wake me up alarm and timer`() {
        val alarm = CommandParser.parse("wake me up at 8pm")
        assertTrue(alarm is JarvisCommand.SetAlarm)
        assertEquals(20, (alarm as JarvisCommand.SetAlarm).hour)
        assertEquals(0, (alarm as JarvisCommand.SetAlarm).minute)

        val timer = CommandParser.parse("wake me up after 10 mins")
        assertTrue(timer is JarvisCommand.SetTimer)
        assertEquals(600, (timer as JarvisCommand.SetTimer).seconds)
    }

    @Test
    fun `time and date queries`() {
        assertEquals(JarvisCommand.GetTime, CommandParser.parse("what time now"))
        assertEquals(JarvisCommand.GetDate, CommandParser.parse("what day is today"))
    }

    @Test
    fun `call incoming actions`() {
        assertEquals(JarvisCommand.SilenceRinger, CommandParser.parse("silence call"))
        assertEquals(JarvisCommand.AnswerCall, CommandParser.parse("accept"))
        assertEquals(JarvisCommand.DeclineCall, CommandParser.parse("decline"))
    }

    @Test
    fun `open copied link`() {
        assertEquals(JarvisCommand.OpenCopiedLink, CommandParser.parse("open the link i copied"))
        assertEquals(JarvisCommand.OpenCopiedLink, CommandParser.parse("open copied link"))
    }

    @Test
    fun `parseCandidates picks valid candidate over unknown candidate 0`() {
        val candidates = listOf("cool teja on speaker", "call teja on speaker", "all teja")
        val (cmd, text) = CommandParser.parseCandidates(candidates)
        assertTrue(cmd is JarvisCommand.CallContact)
        assertEquals("call teja on speaker", text)
        assertEquals("teja", (cmd as JarvisCommand.CallContact).name)
        assertTrue(cmd.onSpeaker)
    }

    @Test
    fun `homophone corrections`() {
        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("touch on"))
        val call = CommandParser.parse("coal teja")
        assertTrue(call is JarvisCommand.CallContact)
        assertEquals("teja", (call as JarvisCommand.CallContact).name)
    }

    @Test
    fun `v2 screenshot and accessibility actions`() {
        assertEquals(JarvisCommand.TakeScreenshot, CommandParser.parse("take a screenshot"))
        assertEquals(JarvisCommand.TakeScreenshot, CommandParser.parse("screenshot"))
        assertEquals(JarvisCommand.OpenNotificationsPanel, CommandParser.parse("open notifications"))
        assertEquals(JarvisCommand.OpenQuickSettings, CommandParser.parse("quick settings"))
        assertEquals(JarvisCommand.OpenPowerMenu, CommandParser.parse("power menu"))
        assertEquals(JarvisCommand.ToggleSplitScreen, CommandParser.parse("split screen"))
    }

    @Test
    fun `v2 notification reader commands`() {
        assertEquals(JarvisCommand.ReadNotifications, CommandParser.parse("read notifications"))
        assertEquals(JarvisCommand.ReadNotifications, CommandParser.parse("read my notifications"))
        assertEquals(JarvisCommand.ReadMessages, CommandParser.parse("read messages"))
        assertEquals(JarvisCommand.ReadMessages, CommandParser.parse("read whatsapp messages"))
        assertEquals(JarvisCommand.ClearNotifications, CommandParser.parse("clear notifications"))
    }

    @Test
    fun `v2 context memory and relationship commands`() {
        val nameCmd = CommandParser.parse("my name is sarat")
        assertTrue(nameCmd is JarvisCommand.RememberUserName)
        assertEquals("sarat", (nameCmd as JarvisCommand.RememberUserName).name)

        assertEquals(JarvisCommand.GetUserName, CommandParser.parse("what is my name"))
        assertEquals(JarvisCommand.GetUserName, CommandParser.parse("who am i"))

        val relSetCmd = CommandParser.parse("remember teja is my brother")
        assertTrue(relSetCmd is JarvisCommand.RememberRelationship)
        assertEquals("brother", (relSetCmd as JarvisCommand.RememberRelationship).relation)
        assertEquals("teja", relSetCmd.contactName)

        val relGetCmd = CommandParser.parse("who is my brother")
        assertTrue(relGetCmd is JarvisCommand.GetRelationship)
        assertEquals("brother", (relGetCmd as JarvisCommand.GetRelationship).relation)

        assertEquals(JarvisCommand.ClearMemory, CommandParser.parse("clear memory"))
    }

    @Test
    fun `v2 deep media commands`() {
        val spotifyCmd = CommandParser.parse("play believer on spotify")
        assertTrue(spotifyCmd is JarvisCommand.PlayOnSpotify)
        assertEquals("believer", (spotifyCmd as JarvisCommand.PlayOnSpotify).query)

        val ytCmd = CommandParser.parse("play interstellar theme on youtube")
        assertTrue(ytCmd is JarvisCommand.PlayOnYouTube)
        assertEquals("interstellar theme", (ytCmd as JarvisCommand.PlayOnYouTube).query)
    }

    @Test
    fun `v2 focus mode routine and diagnostics`() {
        assertEquals(JarvisCommand.FocusMode, CommandParser.parse("focus mode"))
        assertEquals(JarvisCommand.FocusMode, CommandParser.parse("work mode"))
        assertEquals(JarvisCommand.SystemDiagnostics, CommandParser.parse("status report"))
        assertEquals(JarvisCommand.SystemDiagnostics, CommandParser.parse("system diagnostics"))

        val conv = CommandParser.parse("who are you")
        assertTrue(conv is JarvisCommand.Conversational)
        assertTrue((conv as JarvisCommand.Conversational).reply.contains("Blue"))

        val greet = CommandParser.parse("hello blue")
        assertTrue(greet is JarvisCommand.Conversational)
        assertTrue((greet as JarvisCommand.Conversational).reply.contains("Hey there"))
    }

    @Test
    fun `v3 ai knowledge and definition questions`() {
        val def = CommandParser.parse("define atmosphere")
        assertTrue(def is JarvisCommand.AskAi)
        assertEquals("Define atmosphere", (def as JarvisCommand.AskAi).question)

        val exp = CommandParser.parse("explain quantum computing")
        assertTrue(exp is JarvisCommand.AskAi)
        assertEquals("Explain quantum computing", (exp as JarvisCommand.AskAi).question)

        val why = CommandParser.parse("why is the sky blue")
        assertTrue(why is JarvisCommand.AskAi)

        val who = CommandParser.parse("who is albert einstein")
        assertTrue(who is JarvisCommand.AskAi)

        val what = CommandParser.parse("what is photosynthesis")
        assertTrue(what is JarvisCommand.AskAi)

        // Ensure device status questions are NOT intercepted
        assertEquals(JarvisCommand.GetBattery, CommandParser.parse("what is my battery"))
        assertEquals(JarvisCommand.GetUserName, CommandParser.parse("what is my name"))
        assertEquals(JarvisCommand.GetTime, CommandParser.parse("what time is it"))

        // Ensure relationship queries are NOT intercepted
        val rel = CommandParser.parse("who is my brother")
        assertTrue(rel is JarvisCommand.GetRelationship)
    }

    @Test
    fun `v4 telugu native and teluglish commands`() {
        // Flashlight
        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("టార్చ్ ఆన్ చేయి"))
        assertEquals(JarvisCommand.FlashlightOff, CommandParser.parse("టార్చ్ ఆఫ్ చేయి"))
        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("torch on cheyi"))
        assertEquals(JarvisCommand.FlashlightOff, CommandParser.parse("torch off cheyi"))

        // Battery, Time, Date
        assertEquals(JarvisCommand.GetBattery, CommandParser.parse("బ్యాటరీ ఎంత ఉంది"))
        assertEquals(JarvisCommand.GetBattery, CommandParser.parse("battery entha undi"))
        assertEquals(JarvisCommand.GetTime, CommandParser.parse("సమయం ఎంత"))
        assertEquals(JarvisCommand.GetTime, CommandParser.parse("time entha"))
        assertEquals(JarvisCommand.GetDate, CommandParser.parse("ఈరోజు తేదీ ఎంత"))
        assertEquals(JarvisCommand.GetDate, CommandParser.parse("eroju date entha"))

        // Volume
        assertEquals(JarvisCommand.VolumeUp, CommandParser.parse("వాల్యూమ్ పెంచు"))
        assertEquals(JarvisCommand.VolumeDown, CommandParser.parse("వాల్యూమ్ తగ్గించు"))
        assertEquals(JarvisCommand.VolumeDown, CommandParser.parse("volume thagginchu"))
        assertEquals(JarvisCommand.MuteMedia, CommandParser.parse("మ్యూట్ చేయి"))

        // Reels & Gestures
        assertEquals(JarvisCommand.NextReel, CommandParser.parse("నెక్స్ట్ రీల్"))
        assertEquals(JarvisCommand.NextReel, CommandParser.parse("తరువాతి రీల్"))
        assertEquals(JarvisCommand.PreviousReel, CommandParser.parse("మునుపటి రీల్"))
        assertEquals(JarvisCommand.LikeReel, CommandParser.parse("లైక్ చేయి"))
        assertEquals(JarvisCommand.LikeReel, CommandParser.parse("like cheyi"))
        assertEquals(JarvisCommand.LockScreen, CommandParser.parse("లాక్ చేయి"))
        assertEquals(JarvisCommand.TakeScreenshot, CommandParser.parse("స్క్రీన్‌షాట్ తీయి"))
        assertEquals(JarvisCommand.GoHome, CommandParser.parse("హోమ్ కి వెళ్ళు"))
        assertEquals(JarvisCommand.GoBack, CommandParser.parse("వెనక్కి వెళ్ళు"))

        // Calling
        val telCall = CommandParser.parse("సురేష్ కి కాల్ చేయి")
        assertTrue(telCall is JarvisCommand.CallContact)
        assertEquals("సురేష్", (telCall as JarvisCommand.CallContact).name)

        val teluglishCall = CommandParser.parse("suresh ki call cheyi")
        assertTrue(teluglishCall is JarvisCommand.CallContact)
        assertEquals("suresh", (teluglishCall as JarvisCommand.CallContact).name)

        // WhatsApp & Apps
        assertTrue(CommandParser.parse("వాట్సాప్ ఓపెన్ చేయి") is JarvisCommand.WhatsAppOpen)

        val yt = CommandParser.parse("యూట్యూబ్ ఓపెన్ చేయి")
        assertTrue(yt is JarvisCommand.OpenApp)
        assertEquals("youtube", (yt as JarvisCommand.OpenApp).appNameRaw)

        // Conversational Telugu
        val who = CommandParser.parse("నువ్వు ఎవరు")
        assertTrue(who is JarvisCommand.Conversational)
        assertTrue((who as JarvisCommand.Conversational).reply.contains("బ్లూ"))

        val how = CommandParser.parse("ఎలా ఉన్నావు")
        assertTrue(how is JarvisCommand.Conversational)

        // Telugu AI Knowledge Queries
        val teAi = CommandParser.parse("భూమి అంటే ఏమిటి")
        assertTrue(teAi is JarvisCommand.AskAi)

        val tgAi = CommandParser.parse("quantum computing ante enti")
        assertTrue(tgAi is JarvisCommand.AskAi)
    }

    @Test
    fun `v5 universal relationship memory parsing`() {
        val rel1 = CommandParser.parse("harsha JR is my Bamardhi")
        assertTrue(rel1 is JarvisCommand.RememberRelationship)
        assertEquals("bamardhi", (rel1 as JarvisCommand.RememberRelationship).relation)
        assertEquals("harsha jr", (rel1 as JarvisCommand.RememberRelationship).contactName)

        val rel2 = CommandParser.parse("teja is my brother")
        assertTrue(rel2 is JarvisCommand.RememberRelationship)
        assertEquals("brother", (rel2 as JarvisCommand.RememberRelationship).relation)
        assertEquals("teja", (rel2 as JarvisCommand.RememberRelationship).contactName)

        val query = CommandParser.parse("who is my bamardhi")
        assertTrue(query is JarvisCommand.GetRelationship)
        assertEquals("bamardhi", (query as JarvisCommand.GetRelationship).relation)

        val erase = CommandParser.parse("forget my bamardhi")
        assertTrue(erase is JarvisCommand.EraseRelationship)
        assertEquals("bamardhi", (erase as JarvisCommand.EraseRelationship).relation)

        val erase2 = CommandParser.parse("erase bamardhi")
        assertTrue(erase2 is JarvisCommand.EraseRelationship)
        assertEquals("bamardhi", (erase2 as JarvisCommand.EraseRelationship).relation)

        val callRel = CommandParser.parse("call bamardhi")
        assertTrue(callRel is JarvisCommand.CallContact)
        assertEquals("bamardhi", (callRel as JarvisCommand.CallContact).name)
    }

    @Test
    fun `v5 translation parsing`() {
        val t1 = CommandParser.parse("translate this lambadi to telugu or english")
        assertTrue(t1 is JarvisCommand.Translate)
        val cmd1 = t1 as JarvisCommand.Translate
        assertEquals("telugu or english", cmd1.targetLanguage)
        assertEquals("lambadi", cmd1.sourceLanguage)

        val t2 = CommandParser.parse("translate hello to lambadi")
        assertTrue(t2 is JarvisCommand.Translate)
        val cmd2 = t2 as JarvisCommand.Translate
        assertEquals("hello", cmd2.text)
        assertEquals("lambadi", cmd2.targetLanguage)

        val t3 = CommandParser.parse("translate this to hindi")
        assertTrue(t3 is JarvisCommand.Translate)
        assertEquals("hindi", (t3 as JarvisCommand.Translate).targetLanguage)
    }

    @Test
    fun `v5 look at my screen vision`() {
        assertEquals(JarvisCommand.LookAtScreen, CommandParser.parse("look at my screen"))
        assertEquals(JarvisCommand.LookAtScreen, CommandParser.parse("explain this screen"))
        assertEquals(JarvisCommand.LookAtScreen, CommandParser.parse("screen choodu"))
        assertEquals(JarvisCommand.LookAtScreen, CommandParser.parse("meri screen dekho"))
    }

    @Test
    fun `v5 continuous chat mode`() {
        assertEquals(JarvisCommand.ContinuousChatMode(true), CommandParser.parse("start chat mode"))
        assertEquals(JarvisCommand.ContinuousChatMode(true), CommandParser.parse("chat mode on"))
        assertEquals(JarvisCommand.ContinuousChatMode(false), CommandParser.parse("stop chat mode"))
        assertEquals(JarvisCommand.ContinuousChatMode(false), CommandParser.parse("chat mode off"))
    }

    @Test
    fun `v5 custom workflows`() {
        val wfExec = CommandParser.parse("run workflow gaming")
        assertTrue(wfExec is JarvisCommand.ExecuteWorkflow)
        assertEquals("gaming", (wfExec as JarvisCommand.ExecuteWorkflow).name)

        val wfCreate = CommandParser.parse("create workflow gaming steps volume 12 and then torch off")
        assertTrue(wfCreate is JarvisCommand.CreateWorkflow)
        val cmd = wfCreate as JarvisCommand.CreateWorkflow
        assertEquals("gaming", cmd.name)
        assertEquals(listOf("volume 12", "torch off"), cmd.actions)
    }

    @Test
    fun `v5 hindi commands`() {
        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("torch on karo"))
        assertEquals(JarvisCommand.FlashlightOff, CommandParser.parse("torch off karo"))
        assertEquals(JarvisCommand.GetBattery, CommandParser.parse("battery kitni hai"))
        assertEquals(JarvisCommand.GetTime, CommandParser.parse("samay kya hai"))
        assertEquals(JarvisCommand.NextReel, CommandParser.parse("agli reel"))
        assertEquals(JarvisCommand.PreviousReel, CommandParser.parse("pichli reel"))
        assertEquals(JarvisCommand.AnswerCall, CommandParser.parse("call uthao"))
        assertEquals(JarvisCommand.DeclineCall, CommandParser.parse("call kaato"))
    }

    @Test
    fun `v5 lambadi commands`() {
        val ram = CommandParser.parse("ram ram")
        assertTrue(ram is JarvisCommand.Conversational)
        assertTrue((ram as JarvisCommand.Conversational).reply.contains("Ram Ram"))

        val name = CommandParser.parse("tharo naam kai che")
        assertTrue(name is JarvisCommand.Conversational)
        assertTrue((name as JarvisCommand.Conversational).reply.contains("Blue"))

        assertEquals(JarvisCommand.FlashlightOn, CommandParser.parse("light laga"))
        assertEquals(JarvisCommand.FlashlightOff, CommandParser.parse("light bujha"))
    }

    @Test
    fun `unknown command`() {
        val cmd = CommandParser.parse("xyz")
        assertTrue(cmd is JarvisCommand.OpenApp || cmd is JarvisCommand.Unknown)
    }
}
