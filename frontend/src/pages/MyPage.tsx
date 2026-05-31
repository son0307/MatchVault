import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { KeyRound, Star, Trash2, UserRound } from "lucide-react";
import type { LeagueAuthState } from "../App";
import {
  changePassword,
  deleteAccount,
  fetchFavoriteDashboard,
  removeFavoritePlayer,
  removeFavoriteTeam,
  updateNickname,
  type FavoriteDashboard,
  type FavoritePlayerCard,
  type FavoriteTeamCard,
} from "../api";

const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 20;

export function MyPage({ authState, season }: { authState: LeagueAuthState; season: number }) {
  const navigate = useNavigate();
  const [favorites, setFavorites] = useState<FavoriteDashboard | null>(null);
  const [isLoadingFavorites, setIsLoadingFavorites] = useState(false);
  const [favoritesError, setFavoritesError] = useState("");
  const [pendingFavoriteKey, setPendingFavoriteKey] = useState("");
  const favoritesRequestIdRef = useRef(0);

  const [nickname, setNickname] = useState("");
  const [nicknameMessage, setNicknameMessage] = useState("");
  const [nicknameError, setNicknameError] = useState("");
  const [isSavingNickname, setIsSavingNickname] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [isSavingPassword, setIsSavingPassword] = useState(false);

  const [deletePassword, setDeletePassword] = useState("");
  const [deleteError, setDeleteError] = useState("");
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);

  useEffect(() => {
    if (authState.authStatus !== "authenticated") {
      setNickname("");
      setFavorites(null);
      setFavoritesError("");
      setIsLoadingFavorites(false);
      return;
    }
    setNickname(authState.currentUser?.nickname ?? "");
  }, [authState.authStatus, authState.currentUser?.nickname]);

  useEffect(() => {
    if (authState.authStatus !== "authenticated") {
      favoritesRequestIdRef.current += 1;
      setFavorites(null);
      setFavoritesError("");
      setIsLoadingFavorites(false);
      return;
    }
    void loadFavorites();
  }, [authState.authStatus, season]);

  const hasFavorites = useMemo(
    () => Boolean((favorites?.teams ?? []).length || (favorites?.players ?? []).length),
    [favorites],
  );

  if (authState.authStatus === "checking") {
    return (
      <section className="league-content">
        <article className="panel placeholder-panel">로그인 상태를 확인하는 중입니다.</article>
      </section>
    );
  }

  if (authState.authStatus === "guest") {
    return <Navigate to="/login" replace />;
  }

  async function loadFavorites() {
    const requestId = favoritesRequestIdRef.current + 1;
    favoritesRequestIdRef.current = requestId;
    setIsLoadingFavorites(true);
    setFavoritesError("");
    try {
      const nextFavorites = await fetchFavoriteDashboard(season);
      if (requestId === favoritesRequestIdRef.current) {
        setFavorites(nextFavorites);
      }
    } catch (error) {
      if (requestId !== favoritesRequestIdRef.current) {
        return;
      }
      setFavorites(null);
      setFavoritesError(error instanceof Error ? error.message : "즐겨찾기를 불러오지 못했습니다.");
    } finally {
      if (requestId === favoritesRequestIdRef.current) {
        setIsLoadingFavorites(false);
      }
    }
  }

  async function handleNicknameSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNicknameMessage("");
    setNicknameError("");
    const nextNickname = nickname.trim();
    if (nextNickname.length < 2 || nextNickname.length > 20) {
      setNicknameError("닉네임은 2자 이상 20자 이하로 입력해 주세요.");
      return;
    }

    setIsSavingNickname(true);
    try {
      const updatedUser = await updateNickname(nextNickname);
      authState.setCurrentUser(updatedUser);
      authState.setAuthStatus("authenticated");
      setNicknameMessage("닉네임을 변경했습니다.");
    } catch (error) {
      setNicknameError(error instanceof Error ? error.message : "닉네임 변경에 실패했습니다.");
    } finally {
      setIsSavingNickname(false);
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordMessage("");
    setPasswordError("");
    if (!currentPassword.trim() || !newPassword.trim()) {
      setPasswordError("현재 비밀번호와 새 비밀번호를 모두 입력해 주세요.");
      return;
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH || newPassword.length > MAX_PASSWORD_LENGTH) {
      setPasswordError("새 비밀번호는 8자 이상 20자 이하로 입력해 주세요.");
      return;
    }

    setIsSavingPassword(true);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setPasswordMessage("비밀번호를 변경했습니다.");
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : "비밀번호 변경에 실패했습니다.");
    } finally {
      setIsSavingPassword(false);
    }
  }

  async function handleDeleteAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDeleteError("");
    if (!deletePassword.trim()) {
      setDeleteError("현재 비밀번호를 입력해 주세요.");
      return;
    }
    if (!window.confirm("회원 탈퇴 후 계정과 즐겨찾기를 되돌릴 수 없습니다. 탈퇴할까요?")) {
      return;
    }

    setIsDeletingAccount(true);
    try {
      await deleteAccount(deletePassword);
      authState.setCurrentUser(null);
      authState.setAuthStatus("guest");
      navigate("/league/overview", { replace: true });
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : "회원 탈퇴에 실패했습니다.");
    } finally {
      setIsDeletingAccount(false);
    }
  }

  async function handleRemoveTeam(teamId: number) {
    if (!Number.isFinite(teamId) || teamId <= 0) {
      return;
    }
    const key = `team-${teamId}`;
    const requestId = favoritesRequestIdRef.current + 1;
    favoritesRequestIdRef.current = requestId;
    setPendingFavoriteKey(key);
    setFavoritesError("");
    try {
      const nextFavorites = await removeFavoriteTeam(teamId, season);
      if (requestId === favoritesRequestIdRef.current) {
        setFavorites(nextFavorites);
      }
    } catch (error) {
      if (requestId !== favoritesRequestIdRef.current) {
        return;
      }
      setFavoritesError(error instanceof Error ? error.message : "팀 즐겨찾기 삭제에 실패했습니다.");
    } finally {
      if (requestId === favoritesRequestIdRef.current) {
        setPendingFavoriteKey("");
      }
    }
  }

  async function handleRemovePlayer(playerId: number) {
    if (!Number.isFinite(playerId) || playerId <= 0) {
      return;
    }
    const key = `player-${playerId}`;
    const requestId = favoritesRequestIdRef.current + 1;
    favoritesRequestIdRef.current = requestId;
    setPendingFavoriteKey(key);
    setFavoritesError("");
    try {
      const nextFavorites = await removeFavoritePlayer(playerId, season);
      if (requestId === favoritesRequestIdRef.current) {
        setFavorites(nextFavorites);
      }
    } catch (error) {
      if (requestId !== favoritesRequestIdRef.current) {
        return;
      }
      setFavoritesError(error instanceof Error ? error.message : "선수 즐겨찾기 삭제에 실패했습니다.");
    } finally {
      if (requestId === favoritesRequestIdRef.current) {
        setPendingFavoriteKey("");
      }
    }
  }

  return (
    <section className="league-content mypage">
      <article className="panel mypage-profile-panel">
        <div className="panel-heading">
          <div className="panel-title-with-icon">
            <UserRound size={20} aria-hidden="true" />
            <h2>마이 페이지</h2>
          </div>
        </div>
        <div className="mypage-profile">
          <strong>{authState.currentUser?.nickname || authState.currentUser?.email}</strong>
          <span>{authState.currentUser?.email}</span>
        </div>
      </article>

      <div className="mypage-grid">
        <article className="panel account-panel">
          <div className="panel-heading">
            <h2>닉네임 관리</h2>
          </div>
          <form className="account-form" onSubmit={handleNicknameSubmit}>
            <label>
              닉네임
              <input
                maxLength={20}
                minLength={2}
                onChange={(event) => setNickname(event.target.value)}
                required
                type="text"
                value={nickname}
              />
            </label>
            {nicknameError ? <p className="form-message error">{nicknameError}</p> : null}
            {nicknameMessage ? <p className="form-message success">{nicknameMessage}</p> : null}
            <button className="auth-submit-button" disabled={isSavingNickname} type="submit">
              {isSavingNickname ? "저장 중" : "닉네임 변경"}
            </button>
          </form>
        </article>

        <article className="panel account-panel">
          <div className="panel-heading">
            <div className="panel-title-with-icon">
              <KeyRound size={20} aria-hidden="true" />
              <h2>비밀번호 관리</h2>
            </div>
          </div>
          <form className="account-form" onSubmit={handlePasswordSubmit}>
            <label>
              현재 비밀번호
              <input
                autoComplete="current-password"
                onChange={(event) => setCurrentPassword(event.target.value)}
                required
                type="password"
                value={currentPassword}
              />
            </label>
            <label>
              새 비밀번호
              <input
                autoComplete="new-password"
                maxLength={MAX_PASSWORD_LENGTH}
                minLength={MIN_PASSWORD_LENGTH}
                onChange={(event) => setNewPassword(event.target.value)}
                required
                type="password"
                value={newPassword}
              />
            </label>
            {passwordError ? <p className="form-message error">{passwordError}</p> : null}
            {passwordMessage ? <p className="form-message success">{passwordMessage}</p> : null}
            <button className="auth-submit-button" disabled={isSavingPassword} type="submit">
              {isSavingPassword ? "변경 중" : "비밀번호 변경"}
            </button>
          </form>
        </article>
      </div>

      <article className="panel favorites-manager-panel">
        <div className="panel-heading">
          <div className="panel-title-with-icon">
            <Star size={20} aria-hidden="true" />
            <h2>즐겨찾기 관리</h2>
          </div>
        </div>
        {favoritesError ? (
          <div className="section-error inline-retry">
            <span>{favoritesError}</span>
            <button type="button" onClick={() => void loadFavorites()}>
              다시 불러오기
            </button>
          </div>
        ) : null}
        {isLoadingFavorites ? (
          <div className="empty-state">즐겨찾기를 불러오는 중입니다.</div>
        ) : hasFavorites ? (
          <div className="favorites-manage-grid">
            <FavoriteTeamManageList
              onRemove={handleRemoveTeam}
              pendingFavoriteKey={pendingFavoriteKey}
              teams={favorites?.teams ?? []}
            />
            <FavoritePlayerManageList
              onRemove={handleRemovePlayer}
              pendingFavoriteKey={pendingFavoriteKey}
              players={favorites?.players ?? []}
            />
          </div>
        ) : (
          <div className="empty-state">아직 즐겨찾기한 팀이나 선수가 없습니다.</div>
        )}
      </article>

      <article className="panel danger-panel">
        <div className="panel-heading">
          <div className="panel-title-with-icon">
            <Trash2 size={20} aria-hidden="true" />
            <h2>회원 탈퇴</h2>
          </div>
        </div>
        <form className="account-form" onSubmit={handleDeleteAccount}>
          <label>
            현재 비밀번호
            <input
              autoComplete="current-password"
              onChange={(event) => setDeletePassword(event.target.value)}
              required
              type="password"
              value={deletePassword}
            />
          </label>
          {deleteError ? <p className="form-message error">{deleteError}</p> : null}
          <button className="danger-button" disabled={isDeletingAccount} type="submit">
            {isDeletingAccount ? "탈퇴 처리 중" : "회원 탈퇴"}
          </button>
        </form>
      </article>
    </section>
  );
}

function FavoriteTeamManageList({
  teams,
  pendingFavoriteKey,
  onRemove,
}: {
  teams: FavoriteTeamCard[];
  pendingFavoriteKey: string;
  onRemove: (teamId: number) => void;
}) {
  return (
    <section className="favorite-manage-section">
      <h3>팀</h3>
      {teams.length ? (
        <div className="favorite-manage-list">
          {teams.map((team) => (
            <article className="favorite-manage-card" key={team.teamId}>
              <div className="favorite-mini-head">
                {team.logoUrl ? <img src={team.logoUrl} alt="" className="team-logo" /> : <span className="team-logo placeholder" />}
                <div>
                  <strong>{team.teamName ?? "-"}</strong>
                  <p>{numberText(team.rank)}위 · {numberText(team.points)}점 · 최근 {team.form ?? "-"}</p>
                </div>
              </div>
              <p className="favorite-line">
                {team.liveFixture
                  ? `LIVE ${team.liveFixture.homeTeamName} ${team.liveFixture.homeScore}:${team.liveFixture.awayScore} ${team.liveFixture.awayTeamName}`
                  : team.nextFixture
                    ? `다음 경기 · ${team.nextFixture.homeTeamName} vs ${team.nextFixture.awayTeamName}`
                    : "예정된 경기 정보가 없습니다."}
              </p>
              <button
                className="favorite-remove-button"
                disabled={pendingFavoriteKey === `team-${team.teamId}`}
                onClick={() => onRemove(team.teamId)}
                type="button"
              >
                삭제
              </button>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state compact">즐겨찾기한 팀이 없습니다.</div>
      )}
    </section>
  );
}

function FavoritePlayerManageList({
  players,
  pendingFavoriteKey,
  onRemove,
}: {
  players: FavoritePlayerCard[];
  pendingFavoriteKey: string;
  onRemove: (playerId: number) => void;
}) {
  return (
    <section className="favorite-manage-section">
      <h3>선수</h3>
      {players.length ? (
        <div className="favorite-manage-list">
          {players.map((player) => (
            <FavoritePlayerManageCard
              key={player.playerId}
              onRemove={onRemove}
              pendingFavoriteKey={pendingFavoriteKey}
              player={player}
            />
          ))}
        </div>
      ) : (
        <div className="empty-state compact">즐겨찾기한 선수가 없습니다.</div>
      )}
    </section>
  );
}

function FavoritePlayerManageCard({
  player,
  pendingFavoriteKey,
  onRemove,
}: {
  player: FavoritePlayerCard;
  pendingFavoriteKey: string;
  onRemove: (playerId: number) => void;
}) {
  const seasonStat = player.seasonStat;

  return (
    <article className="favorite-manage-card">
      <div className="favorite-mini-head">
        {player.photoUrl ? <img src={player.photoUrl} alt="" className="player-thumb" /> : <span className="player-thumb placeholder" />}
        <div>
          <strong>{player.playerName ?? "-"}</strong>
          <p>{player.position ?? "Player"} · {seasonStat?.teamName ?? "-"}</p>
        </div>
      </div>
      {seasonStat ? (
        <>
          <p className="favorite-line">
            시즌 {numberText(seasonStat.goals)}골 {numberText(seasonStat.assists)}도움 · 평점 {numberText(seasonStat.rating)}
          </p>
        </>
      ) : (
        <p className="favorite-line muted">해당 선수는 이번 시즌에 EPL 기록이 없습니다.</p>
      )}
      <button
        className="favorite-remove-button"
        disabled={pendingFavoriteKey === `player-${player.playerId}`}
        onClick={() => onRemove(player.playerId)}
        type="button"
      >
        삭제
      </button>
    </article>
  );
}

function numberText(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : String(value);
}
