import LoginForm from '../components/login/LoginForm';
import styles from './Sign.module.css';

const Login = () => {
  return (
    <div className={styles.loginContainer}>
      <LoginForm />
    </div>
  );
};

export default Login;
