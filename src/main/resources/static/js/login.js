const container = document.getElementById("container");
const signInPanel = document.getElementById("signInPanel");
const signUpPanel = document.getElementById("signUpPanel");
const overlayContainer = document.getElementById("overlayContainer");
const overlay = document.getElementById("overlay");
const overlayLeft = document.getElementById("overlayLeft");
const overlayRight = document.getElementById("overlayRight");
const signUpButton = document.getElementById("signUp");
const signInButton = document.getElementById("signIn");

const loginForm = document.getElementById("login-form");
const signupForm = document.getElementById("signup-form");

const setMode = (isSignup) => {
    signInPanel.classList.toggle("translate-x-full", isSignup);
    signInPanel.classList.toggle("opacity-0", isSignup);
    signInPanel.classList.toggle("z-10", isSignup);
    signInPanel.classList.toggle("z-20", !isSignup);

    signUpPanel.classList.toggle("translate-x-full", isSignup);
    signUpPanel.classList.toggle("opacity-100", isSignup);
    signUpPanel.classList.toggle("opacity-0", !isSignup);
    signUpPanel.classList.toggle("z-20", isSignup);
    signUpPanel.classList.toggle("z-10", !isSignup);

    overlayContainer.classList.toggle("-translate-x-full", isSignup);
    overlay.classList.toggle("translate-x-1/2", isSignup);

    overlayLeft.classList.toggle("translate-x-0", isSignup);
    overlayLeft.classList.toggle("opacity-100", isSignup);
    overlayLeft.classList.toggle("-translate-x-[20%]", !isSignup);
    overlayLeft.classList.toggle("opacity-0", !isSignup);

    overlayRight.classList.toggle("translate-x-[20%]", isSignup);
    overlayRight.classList.toggle("opacity-0", isSignup);
    overlayRight.classList.toggle("translate-x-0", !isSignup);
    overlayRight.classList.toggle("opacity-100", !isSignup);
};

setMode(false);

signUpButton.addEventListener("click", () => setMode(true));
signInButton.addEventListener("click", () => setMode(false));

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const username = document.getElementById("login-identifier").value.trim();
    const password = document.getElementById("login-password").value;

    try {
        const res = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password }),
        });
        const data = await res.json();
        if (!res.ok) {
            alert(data.message || "Login failed");
            return;
        }
        if (data.token) {
            localStorage.setItem("accessToken", data.token);
        }
        alert("Login successful");
    } catch (err) {
        console.error(err);
        alert("Login failed: Server error");
    }
});

signupForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const fullName = document.getElementById("signup-fullname").value.trim();
    const username = document.getElementById("signup-username").value.trim();
    const email = document.getElementById("signup-email").value.trim();
    const password = document.getElementById("signup-password").value;

    try {
        const res = await fetch("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, email, password, fullName }),
        });

        if (res.ok) {
            alert("Registration successful, please log in");
            setMode(false);
        } else {
            const data = await res.json();
            alert(data.message || "Failed to register");
        }
    } catch (err) {
        console.error(err);
        alert("Error occurred during registration");
    }
});
