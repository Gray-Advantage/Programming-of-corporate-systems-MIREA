package space.grayt.teremok.client.contract;

public record AuthCredentialsRequest(
        String login,
        String password) {
}
