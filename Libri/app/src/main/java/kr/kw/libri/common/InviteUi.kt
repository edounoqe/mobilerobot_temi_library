package kr.kw.libri.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.kw.libri.backend.GameInvites
import kr.kw.libri.backend.InviteGame
import kr.kw.libri.backend.LocalGameInvites
import kr.kw.libri.backend.PAIRING_TIMEOUT_MS
import kr.kw.libri.backend.floorLabel

/*
 * 게임 신청 화면 조각
 *  · MultiplayerPanel     — 대기실에서 Multi Play 를 골랐을 때: 기기 목록 + 신청 / 답 기다리기 / 취소
 *  · IncomingInviteDialog — 받은 쪽: 어느 화면 위에든 뜨는 수락 / 거절 팝업
 */

/** 신청 화면이 쓰는 시계 — 0.5초마다 바뀌고, 그때 만료된 신청을 정리한다 */
@Composable
private fun rememberInviteClock(invites: GameInvites): Long {
    var now by remember { mutableLongStateOf(invites.now()) }
    LaunchedEffect(invites) {
        while (true) {
            now = invites.now()
            invites.tick(now)
            delay(500)
        }
    }
    return now
}

private fun secondsLeft(createdAt: Long, now: Long): Int =
    ((PAIRING_TIMEOUT_MS - (now - createdAt)) / 1000).toInt().coerceAtLeast(0)

/**
 * 멀티플레이 — 대결할 기기를 골라 신청한다. 상대가 수락하면 MainActivity 가 두 기기를 같은 게임방에 넣는다.
 * @param blocked 신청할 수 없는 이유 (예: Firebase 승인 전). null 이면 신청할 수 있다
 */
@Composable
fun MultiplayerPanel(game: InviteGame, modifier: Modifier = Modifier, blocked: String? = null) {
    val invites = LocalGameInvites.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibriLabel("대결할 기기를 골라 신청하세요")
        if (invites == null || blocked != null) {
            Text(
                blocked ?: "멀티플레이는 관리자가 로봇 설정에서 층을 정하고 Firebase 승인을 받은 뒤에 쓸 수 있어요.",
                color = LibriColors.Stamp, fontSize = 15.sp, lineHeight = 21.sp,
            )
            return@Column
        }
        val now = rememberInviteClock(invites)
        val out = invites.outgoing
        if (out != null) {
            // 답 기다리는 중
            SketchCard(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TitleText("${floorLabel(out.to)}의 답을 기다리는 중…", size = 24.sp, align = TextAlign.Center)
                NoteText("${out.game.title} 대결 신청 · ${secondsLeft(out.createdAt, now)}초 남음", size = 19.sp, color = LibriColors.Pencil)
                LibriButton("신청 취소", { invites.cancel() }, Modifier.fillMaxWidth(), style = LibriButtonStyle.FLAT, minHeight = 52.dp, fontSize = 18)
            }
        } else {
            if (!invites.connected) Text("서버에 연결하는 중…", color = LibriColors.Pencil, fontSize = 15.sp)
            for (peer in invites.peers) {
                SketchSurface(Modifier.fillMaxWidth(), radius = 18.dp, shadow = 4.dp, seed = peer.id.hashCode(), fillContent = true) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(14.dp).background(
                                when {
                                    peer.available -> LibriColors.Mint
                                    peer.online -> LibriColors.Amber
                                    else -> LibriColors.Faint
                                },
                                CircleShape,
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(floorLabel(peer.id), fontSize = 21.sp, fontFamily = LibriFonts.Round, color = LibriColors.Ink)
                            Text(peer.stateLabel, fontSize = 15.sp, color = LibriColors.Pencil)
                        }
                        LibriButton(
                            "신청", { invites.send(game, peer.id) },
                            style = LibriButtonStyle.COLOR, color = LibriColors.Mint,
                            enabled = invites.connected && peer.available && !invites.saving && invites.incoming(now) == null,
                            minHeight = 52.dp, fontSize = 19,
                        )
                    }
                }
            }
        }
        invites.message?.let { msg ->
            LaunchedEffect(msg) { delay(6000); invites.clearMessage() }
            Text(msg, color = LibriColors.Stamp, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

/**
 * 받은 신청 팝업 — 화면 전체를 살짝 덮고 가운데에 수락 / 거절 카드를 띄운다.
 * MainActivity 가 모든 화면 위에 둔다 (실제 게임 중에는 상대가 신청할 수 없게 막혀 있다).
 */
@Composable
fun IncomingInviteDialog(invites: GameInvites, modifier: Modifier = Modifier) {
    val now = rememberInviteClock(invites)
    val inv = invites.incoming(now) ?: return
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { },   // 뒤 화면 터치 막기
        contentAlignment = Alignment.Center,
    ) {
        SketchCard(
            Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth(),
            seed = 23,
            padding = PaddingValues(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibriTag("게임 신청", LibriColors.Yellow, fontSize = 17.sp)
            TitleText("${floorLabel(inv.from)}이(가)\n${inv.game.title} 대결을 신청했어요!", size = 30.sp, align = TextAlign.Center)
            NoteText("${secondsLeft(inv.createdAt, now)}초 안에 답해 주세요", size = 19.sp, color = LibriColors.Pencil)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                LibriButton(
                    "거절", { invites.respond(false) }, Modifier.weight(1f),
                    style = LibriButtonStyle.FLAT, enabled = !invites.saving, minHeight = 72.dp, fontSize = 22,
                )
                LibriButton(
                    "수락", { invites.respond(true) }, Modifier.weight(1.4f),
                    style = LibriButtonStyle.COLOR, color = LibriColors.Mint, enabled = !invites.saving, minHeight = 72.dp, fontSize = 26,
                    sub = "바로 ${inv.game.title} 시작",
                )
            }
        }
    }
}
