import SignUpForm from '../components/signup/SignUpForm';
import styles from './Sign.module.css';

const SignUp = () => {
  return (
    <div className={styles.signUpContainer}>
      <SignUpForm />
    </div>
  );
};

export default SignUp;
