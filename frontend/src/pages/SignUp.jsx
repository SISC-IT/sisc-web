import SignUpForm from '../components/signup/SignUpForm';
import styles from './Login.module.css';

const SignUp = () => {
  return (
    <div className={styles.signUpContainer}>
      <SignUpForm />
    </div>
  );
};

export default SignUp;
