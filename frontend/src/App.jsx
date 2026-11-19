import { useEffect, useState } from "react";
import "./App.css";

const API_URL = "http://localhost:8081";

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
});

function money(value) {
  return currency.format(Number(value ?? 0));
}

function transactionDescription(transaction, currentEmail) {
  if (transaction.type === "DEPOSIT") {
    return {
      title: "Money added",
      subtitle: "Wallet deposit",
      outgoing: false,
    };
  }

  if (transaction.type === "WITHDRAWAL") {
    return {
      title: "Withdrawal",
      subtitle: "Money withdrawn from wallet",
      outgoing: true,
    };
  }

  const outgoing = transaction.fromEmail === currentEmail;

  return {
    title: outgoing ? "Money sent" : "Money received",
    subtitle: outgoing
        ? `To ${transaction.toPhoneNumber || transaction.toEmail || "another wallet"}`
        : `From ${transaction.fromPhoneNumber || transaction.fromEmail || "another wallet"}`,
    outgoing,
  };
}

function transactionDate(value) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value || "";
  }

  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function App() {
  const [authMode, setAuthMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [registrationPhoneNumber, setRegistrationPhoneNumber] = useState("");
  const [password, setPassword] = useState("");
  const [login, setLogin] = useState(null);

  const [account, setAccount] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [refreshVersion, setRefreshVersion] = useState(0);

  const [action, setAction] = useState("transfer");
  const [recipientPhoneNumber, setRecipientPhoneNumber] = useState("");
  const [amount, setAmount] = useState("");

  const [loginError, setLoginError] = useState("");
  const [registrationError, setRegistrationError] = useState("");
  const [registrationMessage, setRegistrationMessage] = useState("");
  const [accountError, setAccountError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [actionError, setActionError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [lastTransferRequest, setLastTransferRequest] = useState(null);
  const [replayError, setReplayError] = useState("");
  const [replayMessage, setReplayMessage] = useState("");

  const [loginLoading, setLoginLoading] = useState(false);
  const [registrationLoading, setRegistrationLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [replayLoading, setReplayLoading] = useState(false);

  useEffect(() => {
    if (!login?.accessToken) {
      return undefined;
    }

    let cancelled = false;
    const headers = {
      Authorization: `Bearer ${login.accessToken}`,
    };

    async function loadAccount() {
      try {
        const response = await fetch(`${API_URL}/api/v1/accounts/me`, {
          headers,
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
          if (!cancelled) {
            setAccountError(
                result.detail || result.message || "Could not load account."
            );
          }
          return;
        }

        if (!cancelled) {
          setAccount(result);
          setAccountError("");
        }
      } catch (err) {
        if (!cancelled) {
          setAccountError(err.message || "Could not load account.");
        }
      }
    }

    async function loadTransactions() {
      setHistoryLoading(true);

      try {
        const response = await fetch(
            `${API_URL}/api/v1/transactions?page=0&size=5`,
            { headers }
        );
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
          if (!cancelled) {
            setHistoryError(
                result.detail || result.message || "Could not load transactions."
            );
          }
          return;
        }

        if (!cancelled) {
          setTransactions(
              Array.isArray(result.content) ? result.content : []
          );
          setHistoryError("");
        }
      } catch (err) {
        if (!cancelled) {
          setHistoryError(err.message || "Could not load transactions.");
        }
      } finally {
        if (!cancelled) {
          setHistoryLoading(false);
        }
      }
    }

    void loadAccount();
    void loadTransactions();

    return () => {
      cancelled = true;
    };
  }, [login, refreshVersion]);

  async function handleRegister(event) {
    event.preventDefault();
    setRegistrationError("");
    setRegistrationMessage("");
    setRegistrationLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/v1/auth/register`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name,
          email,
          phoneNumber: registrationPhoneNumber,
          password,
        }),
      });

      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setRegistrationError(
          result.detail || result.message || "Could not create your account."
        );
        return;
      }

      setEmail(result.email || email);
      setPassword("");
      setName("");
      setRegistrationPhoneNumber("");
      setAuthMode("login");
      setRegistrationMessage(
        "Account created. Sign in with your email and password."
      );
    } catch (err) {
      setRegistrationError(err.message || "Could not reach the API.");
    } finally {
      setRegistrationLoading(false);
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoginError("");
    setLoginLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/v1/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });

      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setLoginError(
            result.detail ||
            result.message ||
            "Login failed. Check your email and password."
        );
        return;
      }

      setLogin(result);
      setPassword("");
    } catch (err) {
      setLoginError(
          err.message || "Could not reach the API. Check that Spring Boot is running."
      );
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleMoneyAction(event) {
    event.preventDefault();
    setActionError("");
    setActionMessage("");
    setActionLoading(true);

    let endpoint;
    let requestBody;

    if (action === "transfer") {
      endpoint = "/api/v1/transactions/transfer";
      requestBody = {
        recipientPhoneNumber: recipientPhoneNumber.trim(),
        amount: Number(amount),
      };
    } else if (action === "deposit") {
      endpoint = "/api/v1/transactions/deposit";
      requestBody = { amount: Number(amount) };
    } else {
      endpoint = "/api/v1/transactions/withdraw";
      requestBody = { amount: Number(amount) };
    }

    const idempotencyKey = crypto.randomUUID();

    try {
      const response = await fetch(`${API_URL}${endpoint}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${login.accessToken}`,
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify(requestBody),
      });

      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setActionError(
            result.detail || result.message || "The transaction failed."
        );
        return;
      }

      const completedAction =
          action === "transfer"
              ? "Transfer"
              : action === "deposit"
                  ? "Demo deposit"
                  : "Withdrawal";

      setActionMessage(
          `${completedAction} complete. Transaction ID: ${result.transactionId}`
      );
      if (action === "transfer") {
        setLastTransferRequest({
          endpoint,
          requestBody,
          idempotencyKey,
          transactionId: result.transactionId,
        });
        setReplayError("");
        setReplayMessage("");
      }
      setAmount("");
      setRecipientPhoneNumber("");
      setRefreshVersion((version) => version + 1);
    } catch (err) {
      setActionError(err.message || "Could not complete the transaction.");
    } finally {
      setActionLoading(false);
    }
  }

  async function replayLastTransfer() {
    if (!lastTransferRequest || !login?.accessToken) {
      return;
    }

    setReplayError("");
    setReplayMessage("");
    setReplayLoading(true);

    try {
      const response = await fetch(
        `${API_URL}${lastTransferRequest.endpoint}`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${login.accessToken}`,
            "Content-Type": "application/json",
            "Idempotency-Key": lastTransferRequest.idempotencyKey,
          },
          body: JSON.stringify(lastTransferRequest.requestBody),
        }
      );

      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setReplayError(
          result.detail || result.message || `Replay failed (HTTP ${response.status}).`
        );
        return;
      }

      if (
        String(result.transactionId) ===
        String(lastTransferRequest.transactionId)
      ) {
        setReplayMessage(
          `Confirmed: replay returned transaction #${result.transactionId}. No second transfer was created.`
        );
      } else {
        setReplayMessage(
          `Replay returned transaction #${result.transactionId}; original was #${lastTransferRequest.transactionId}.`
        );
      }

      setRefreshVersion((version) => version + 1);
    } catch (err) {
      setReplayError(err.message || "Could not replay the transfer.");
    } finally {
      setReplayLoading(false);
    }
  }

  function chooseAction(nextAction) {
    setAction(nextAction);
    setActionError("");
    setActionMessage("");
    setReplayError("");
    setReplayMessage("");
  }

  function handleLogout() {
    setLogin(null);
    setAccount(null);
    setTransactions([]);
    setEmail("");
    setPassword("");
    setAmount("");
    setRecipientPhoneNumber("");
    setRegistrationPhoneNumber("");
    setLoginError("");
    setAccountError("");
    setHistoryError("");
    setActionError("");
    setActionMessage("");
    setLastTransferRequest(null);
    setReplayError("");
    setReplayMessage("");
  }

  if (!login) {
    const isRegistering = authMode === "register";

    return (
        <main className="auth-page">
          <section className="auth-card">
            <div className="brand">
              <span className="brand-icon">W</span>
              <span>Wallet</span>
            </div>

            <p className="eyebrow">YOUR EVERYDAY WALLET</p>
            <h1>{isRegistering ? "Create your wallet" : "Welcome back"}</h1>
            <p className="muted">
              {isRegistering
                ? "Create an account to start using your wallet."
                : "Sign in to manage your wallet."}
            </p>

            <form
              className="login-form"
              onSubmit={isRegistering ? handleRegister : handleLogin}
            >
              {isRegistering && (
                <>
                  <label htmlFor="name">Full name</label>
                  <input
                    id="name"
                    type="text"
                    autoComplete="name"
                    placeholder="Your name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    required
                  />

                  <label htmlFor="registration-phone">Mobile number</label>
                  <input
                    id="registration-phone"
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    placeholder="10-digit Indian mobile number"
                    pattern="[6-9][0-9]{9}"
                    maxLength={10}
                    value={registrationPhoneNumber}
                    onChange={(event) =>
                      setRegistrationPhoneNumber(
                        event.target.value.replace(/\D/g, "").slice(0, 10)
                      )
                    }
                    required
                  />
                </>
              )}

              <label htmlFor="email">Email address</label>
              <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
              />

              <label htmlFor="password">Password</label>
              <input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
              />

              {loginError && (
                  <p className="error-message" role="alert">
                    {loginError}
                  </p>
              )}

              {registrationError && (
                  <p className="error-message" role="alert">
                    {registrationError}
                  </p>
              )}

              {registrationMessage && (
                  <p className="success-message" role="status">
                    {registrationMessage}
                  </p>
              )}

              <button
                className="primary-button"
                type="submit"
                disabled={isRegistering ? registrationLoading : loginLoading}
              >
                {isRegistering
                  ? registrationLoading
                    ? "Creating account…"
                    : "Create account"
                  : loginLoading
                    ? "Signing in…"
                    : "Sign in"}
              </button>
            </form>

            <p className="fine-print">Your wallet access is protected.</p>
            <p className="auth-switch">
              {isRegistering ? "Already have an account?" : "New to Wallet?"}{" "}
              <button
                className="inline-button"
                type="button"
                onClick={() => {
                  setAuthMode(isRegistering ? "login" : "register");
                  setLoginError("");
                  setRegistrationError("");
                  setRegistrationMessage("");
                }}
              >
                {isRegistering ? "Sign in" : "Create account"}
              </button>
            </p>
          </section>
        </main>
    );
  }

  const actionTitle =
      action === "transfer"
          ? "Send money"
          : action === "deposit"
              ? "Add demo money"
              : "Withdraw money";

  const actionButton =
      action === "transfer"
          ? "Continue transfer"
          : action === "deposit"
              ? "Add demo money"
              : "Withdraw";

  return (
      <div className="app-shell">
        <header className="topbar">
          <a className="brand" href="#wallet" aria-label="Wallet home">
            <span className="brand-icon">W</span>
            <span>Wallet</span>
          </a>

          <div className="topbar-user">
            <div className="avatar">{login.email?.[0]?.toUpperCase() || "U"}</div>
            <span className="topbar-email">{login.email}</span>
            <button className="text-button" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </header>

        <main className="dashboard" id="wallet">
          <section className="welcome-row">
            <div>
              <p className="muted">Your wallet</p>
              <h1>Hi, {login.email?.split("@")[0]}</h1>
            </div>

            <span
                className={`status-pill ${
                    account?.status === "ACTIVE" ? "active" : "inactive"
                }`}
            >
            {account?.status || "Loading account…"}
          </span>
          </section>

          <section className="balance-card">
            <div>
              <p className="balance-label">Available balance</p>
              <p className="balance-amount">
                {account ? money(account.balance) : "Loading…"}
              </p>
              <p className="balance-caption">Your wallet</p>
            </div>
            <div className="balance-symbol" aria-hidden="true">₹</div>
          </section>

          {accountError && (
              <p className="error-message" role="alert">{accountError}</p>
          )}

          <section className="quick-actions" aria-label="Wallet actions">
            <button
                className={`action-tile ${action === "transfer" ? "selected" : ""}`}
                onClick={() => chooseAction("transfer")}
                aria-pressed={action === "transfer"}
            >
              <span className="action-icon send-icon">↗</span>
              <span>Send</span>
            </button>

            <button
                className={`action-tile ${action === "deposit" ? "selected" : ""}`}
                onClick={() => chooseAction("deposit")}
                aria-pressed={action === "deposit"}
            >
              <span className="action-icon add-icon">＋</span>
              <span>Add money</span>
            </button>

            <button
                className={`action-tile ${action === "withdraw" ? "selected" : ""}`}
                onClick={() => chooseAction("withdraw")}
                aria-pressed={action === "withdraw"}
            >
              <span className="action-icon withdraw-icon">↘</span>
              <span>Withdraw</span>
            </button>
          </section>

          <div className="content-grid">
            <section className="panel action-panel">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">WALLET ACTION</p>
                  <h2>{actionTitle}</h2>
                </div>
                <span className="panel-mark">₹</span>
              </div>

              <form className="money-form" onSubmit={handleMoneyAction}>
                {action === "transfer" && (
                    <>
                      <label htmlFor="recipient-phone">Recipient mobile number</label>
                      <input
                          id="recipient-phone"
                          type="tel"
                          inputMode="numeric"
                          autoComplete="tel-national"
                          placeholder="10-digit Indian mobile number"
                          pattern="[6-9][0-9]{9}"
                          maxLength={10}
                          value={recipientPhoneNumber}
                          onChange={(event) =>
                            setRecipientPhoneNumber(
                              event.target.value.replace(/\D/g, "").slice(0, 10)
                            )
                          }
                          required
                      />
                    </>
                )}

                <label htmlFor="amount">Amount</label>
                <div className="amount-input">
                  <span>₹</span>
                  <input
                      id="amount"
                      type="number"
                      min="0.01"
                      step="0.01"
                      placeholder="0.00"
                      value={amount}
                      onChange={(event) => setAmount(event.target.value)}
                      required
                  />
                </div>

                {actionError && (
                    <p className="error-message" role="alert">{actionError}</p>
                )}

                {actionMessage && (
                    <p className="success-message" role="status">{actionMessage}</p>
                )}

                <button
                    className="primary-button"
                    type="submit"
                    disabled={
                        actionLoading ||
                        !account ||
                        account.status !== "ACTIVE"
                    }
                >
                  {actionLoading ? "Processing…" : actionButton}
                </button>

                {action === "deposit" && (
                    <p className="fine-print">
                      Demo deposit only; this does not add real money.
                    </p>
                )}
              </form>

              {action === "transfer" && lastTransferRequest && (
                <div className="idempotency-demo">
                  <strong>Idempotency demo</strong>
                  <p>
                    Replay transfer #{lastTransferRequest.transactionId} using
                    its original request key.
                  </p>
                  <button
                    className="text-button replay-button"
                    type="button"
                    onClick={replayLastTransfer}
                    disabled={replayLoading}
                  >
                    {replayLoading ? "Replaying…" : "Replay same transfer"}
                  </button>
                  {replayError && (
                    <p className="error-message" role="alert">{replayError}</p>
                  )}
                  {replayMessage && (
                    <p className="success-message" role="status">{replayMessage}</p>
                  )}
                </div>
              )}
            </section>

            <section className="panel activity-panel">
              <div className="activity-heading">
                <div>
                  <p className="eyebrow">YOUR WALLET</p>
                  <h2>Recent activity</h2>
                </div>
                <span className="activity-count">
                {transactions.length}
              </span>
              </div>

              {historyError && (
                  <p className="error-message" role="alert">{historyError}</p>
              )}

              {historyLoading && transactions.length === 0 && (
                  <p className="empty-state">Loading transactions…</p>
              )}

              {!historyLoading && !historyError && transactions.length === 0 && (
                  <p className="empty-state">
                    Your completed transactions will appear here.
                  </p>
              )}

              <div className="transaction-list">
                {transactions.map((transaction) => {
                  const details = transactionDescription(
                      transaction,
                      login.email
                  );

                  return (
                      <article
                          className="transaction-row"
                          key={transaction.transactionId}
                      >
                        <div className="transaction-icon">
                          {details.outgoing ? "↗" : "＋"}
                        </div>

                        <div className="transaction-copy">
                          <strong>{details.title}</strong>
                          <span>{details.subtitle}</span>
                          <small>{transactionDate(transaction.createdAt)}</small>
                        </div>

                        <div className="transaction-amount">
                          <strong className={details.outgoing ? "outgoing" : "incoming"}>
                            {details.outgoing ? "−" : "+"}
                            {money(transaction.amount)}
                          </strong>
                          <span>{transaction.status}</span>
                        </div>
                      </article>
                  );
                })}
              </div>
            </section>
          </div>

          <footer className="dashboard-footer">
            Wallet demo <span>•</span> Account ID {account?.id ?? "—"}
          </footer>
        </main>
      </div>
  );
}

export default App;
