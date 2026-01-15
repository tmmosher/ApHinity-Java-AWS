const AphinitySignup = () => {
  return (
    <form class="w-full flex flex-col gap-2 text-left" aria-label="Partner sign up form">
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">ApHinity email</span>
        </div>
        <input
          type="email"
          placeholder="jdoe@aphinitytech.com"
          class="input opacity-70 input-bordered w-full"
          aria-label="ApHinity email"
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
};

export default AphinitySignup;
