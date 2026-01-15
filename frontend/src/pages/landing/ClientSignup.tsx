const ClientSignup = () => {
    return (
        <form class="w-full flex flex-col gap-2 text-left" aria-label="Sign up form">
            <label class="form-control w-full">
                <div class="label">
                    <span class="label-text">Full name</span>
                </div>
                <input
                    type="text"
                    placeholder="John Doe"
                    class="input opacity-70 input-bordered w-full"
                    aria-label="Full name"
                />
            </label>
            <label class="form-control w-full">
                <div class="label">
                    <span class="label-text">Work email</span>
                </div>
                <input
                    type="email"
                    placeholder="john@company.com"
                    class="input opacity-70 input-bordered w-full"
                    aria-label="Work email"
                />
            </label>
            <label class="form-control w-full">
                <div class="label">
                    <span class="label-text">Password</span>
                </div>
                <input
                    type="password"
                    placeholder="A secure password"
                    class="input opacity-70 input-bordered w-full"
                    aria-label="Password"
                />
            </label>
            <button
                type="submit"
                class="btn btn-primary w-full text-center"
                aria-label="Submit sign up form"
            >
                Submit
            </button>
        </form>
    );
}
export default ClientSignup;