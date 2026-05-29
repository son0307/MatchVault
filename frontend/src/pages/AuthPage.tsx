import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LogIn, UserPlus } from "lucide-react";
import { login, signup } from "../api";

const PASSWORD_MIN_LENGTH = 8;

type AuthPageProps = {
  mode: "login" | "signup";
};

export function AuthPage({ mode }: AuthPageProps) {
  const navigate = useNavigate();
  const isSignup = mode === "signup";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validationMessage = validateForm();
    if (validationMessage) {
      setErrorMessage(validationMessage);
      return;
    }

    setIsSubmitting(true);
    setErrorMessage("");

    try {
      if (isSignup) {
        await signup(email.trim(), password, nickname.trim());
      } else {
        await login(email.trim(), password);
      }
      navigate("/league/overview", { replace: true });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "인증 요청에 실패했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function validateForm() {
    if (!email.trim()) {
      return "이메일을 입력해 주세요.";
    }
    if (!password) {
      return "비밀번호를 입력해 주세요.";
    }
    if (password.length < PASSWORD_MIN_LENGTH) {
      return `비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 합니다.`;
    }
    if (isSignup && !nickname.trim()) {
      return "닉네임을 입력해 주세요.";
    }
    return "";
  }

  return (
    <main className="app-shell auth-shell">
      <section className="auth-panel panel">
        <div className="auth-heading">
          <div className="auth-icon" aria-hidden="true">
            {isSignup ? <UserPlus size={24} /> : <LogIn size={24} />}
          </div>
          <div>
            <p className="eyebrow">Soccer Streaming</p>
            <h1>{isSignup ? "회원가입" : "로그인"}</h1>
          </div>
        </div>

        {errorMessage ? <div className="notice error">{errorMessage}</div> : null}

        <form className="auth-page-form" onSubmit={submit}>
          <label>
            <span>이메일</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>

          <label>
            <span>비밀번호</span>
            <input
              type="password"
              autoComplete={isSignup ? "new-password" : "current-password"}
              minLength={PASSWORD_MIN_LENGTH}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>

          {isSignup ? (
            <label>
              <span>닉네임</span>
              <input
                type="text"
                autoComplete="nickname"
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                required
              />
            </label>
          ) : null}

          <button className="auth-submit-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "처리 중..." : isSignup ? "회원가입" : "로그인"}
          </button>
        </form>

        <div className="auth-switch">
          {isSignup ? (
            <>
              이미 계정이 있으신가요? <Link to="/login">로그인</Link>
            </>
          ) : (
            <>
              아직 계정이 없으신가요? <Link to="/signup">회원가입</Link>
            </>
          )}
        </div>
      </section>
    </main>
  );
}
