package com.gachigayo.feature.group

import com.gachigayo.data.route.LngLat
import com.gachigayo.data.route.RouteLine

/**
 * 시연용 그룹: 추석에 세 가구가 각자의 차로 강릉 할머니댁에 간다.
 * 그룹 서버가 붙기 전까지 다른 차의 위치·대답은 앱 안에서 흉내 낸다.
 */
enum class CarKind(val energyLabel: String) { EV("배터리"), GAS("연료"), HYBRID("연료") }

data class Member(
    val id: String,
    val alias: String,
    val short: String,
    val color: Long,
    val kind: CarKind,
    val start: LngLat,
    val energy: Int,
) {
    val colorHex: String get() = "#%06X".format(color and 0xFFFFFF)
}

object GroupDemo {
    const val NAME = "추석 강릉 가는 길"

    val ME = Member("A", "우리집", "우", 0xFF087F70, CarKind.EV, LngLat(127.02761, 37.49789), 41)
    val OTHERS = listOf(
        Member("B", "큰집", "큰", 0xFFE8772E, CarKind.GAS, LngLat(126.9137, 37.5495), 58),
        Member("C", "작은집", "작", 0xFF5468E8, CarKind.HYBRID, LngLat(127.02825, 37.26351), 63),
    )
    val ALL = listOf(ME) + OTHERS

    val RESTS = listOf(
        RestArea("hs", "횡성휴게소", LngLat(128.13462, 37.46486)),
        RestArea("pc", "평창휴게소", LngLat(128.45356, 37.60569)),
    )

    fun member(id: String): Member = ALL.first { it.id == id }
    fun rest(id: String): RestArea = RESTS.first { it.id == id }

    /** 받침이 있으면 '이', 없으면 '가' */
    fun subject(alias: String): String {
        val ch = alias.last()
        val hasBatchim = ch in '가'..'힣' && (ch - '가') % 28 != 0
        return alias + if (hasBatchim) "이" else "가"
    }
}

data class RestArea(val id: String, val name: String, val at: LngLat)

enum class Reason(val label: String, val stayMin: Int) {
    TOILET("화장실", 10),
    REST("쉬어 가기", 15),
    SNACK("간식", 15),
    CHARGE("충전", 25),
    FUEL("주유", 10);

    fun message(rest: String, battery: Int): String = when (this) {
        TOILET -> "화장실이 급해요. $rest 잠깐 들를까요?"
        REST -> "조금 쉬었다 갈까요? $rest 어때요?"
        SNACK -> "${rest}에서 간식 먹고 갈래요?"
        CHARGE -> "배터리가 ${battery}%예요. ${rest}에서 충전하고 가도 될까요?"
        FUEL -> "기름을 넣어야 해요. $rest 들를까요?"
    }
}

enum class ProposalStatus { OPEN, AGREED, DECLINED }

data class Proposal(
    val id: Int,
    val from: String,
    val restId: String,
    val reason: Reason,
    val text: String,
    val votes: Map<String, Boolean?>,
    val status: ProposalStatus = ProposalStatus.OPEN,
    val at: String,
)

/** 함께 가는 다른 차의 상태 */
data class GroupCar(
    val member: Member,
    val line: RouteLine? = null,
    val alongM: Double = 0.0,
    val energy: Int = member.energy,
    val stopAlongM: Double? = null,
    val stopName: String? = null,
    val stayS: Double = 0.0,
    val restLeftS: Double = 0.0,
    val restName: String? = null,
    val arrived: Boolean = false,
    val status: String = "출발 준비 완료",
) {
    val position: LngLat? get() = line?.pointAt(alongM)
}

data class GroupToast(val title: String, val sub: String, val id: Long = System.nanoTime())

// ---------- 함께 놀기 (시연: 음원 재생·차량 제어 없이 화면으로만 보여 준다) ----------

data class Track(val title: String, val lengthS: Int, val requestedBy: String? = null)

object Song {
    const val ARTIST = "같이 가요 플레이리스트"
    val PLAYLIST = listOf(
        Track("함께 떠나는 길", 236),
        Track("가을 고속도로", 214, requestedBy = "C"),
        Track("할머니댁 가는 길", 198, requestedBy = "B"),
    )
}

/** 캐릭터 음성: TTS 높낮이·빠르기로 목소리를 바꾼다 */
enum class Voice(val label: String, val emoji: String, val pitch: Float, val rate: Float) {
    RABBIT("토끼", "🐰", 1.7f, 1.1f),
    BEAR("곰", "🐻", 0.6f, 0.9f),
    ROBOT("로봇", "🤖", 1.0f, 0.75f),
}

data class VoiceMsg(val id: Long, val voice: Voice, val text: String, val seconds: Int, val shared: Int = 3)

val VOICE_PHRASES = listOf(
    "우리 휴게소에서 만나요!",
    "다음 휴게소에서 쉬어요!",
    "할머니댁 가면 뭐 먹을까?",
    "작은집 차 보여요! 손 흔들어 줘!",
)

/** 같이 듣기 제안 (참여 대기) */
data class MusicAsk(val from: String, val joined: Set<String>)

/** 다 같이 듣는 중 */
data class Music(val from: String, val startMs: Long)

data class Penalty(val label: String, val sub: String, val sdk: String)

val PENALTIES = listOf(
    Penalty("동승석 엉따 최강", "시트 히터 3단계 · 60초", "Vehicle SDK · 동승석 시트 히터 3단계 (60초 후 원래대로)"),
    Penalty("동승석 에어컨 최강", "동승석 18℃ · 풍량 최대 · 60초", "Vehicle SDK · 동승석 공조 18℃, 풍량 최대 (60초 후 원래대로)"),
)

enum class RoulettePhase { SPIN, RESULT }

data class Roulette(val by: String, val target: String, val penalty: Penalty, val angle: Float, val phase: RoulettePhase, val id: Long)

/** 걸린 차에 벌칙이 실행되는 중 */
data class PenaltyShow(val car: String, val penalty: Penalty, val id: Long)
