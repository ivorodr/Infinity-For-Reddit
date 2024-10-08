package ml.docilealligator.infinityforreddit.comment;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.thing.UploadedImage;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.markdown.RichTextJSONConverter;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SendComment {
    public static void sendComment(Context context, Executor executor, Handler handler,
                                   String commentMarkdown, String thingFullname, int parentDepth,
                                   List<UploadedImage> uploadedImages,
                                   Retrofit newAuthenticatorOauthRetrofit, Account account,
                                   SendCommentListener sendCommentListener) {
        Map<String, String> headers = APIUtils.getOAuthHeader(account.getAccessToken());
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.API_TYPE_KEY, "json");
        params.put(APIUtils.RETURN_RTJSON_KEY, "true");
        if (!uploadedImages.isEmpty()) {
            try {
                params.put(APIUtils.RICHTEXT_JSON_KEY, new RichTextJSONConverter().constructRichTextJSON(context, commentMarkdown, uploadedImages));
                params.put(APIUtils.TEXT_KEY, "");
            } catch (JSONException e) {
                sendCommentListener.sendCommentFailed(context.getString(R.string.convert_to_richtext_json_failed));
                return;
            }
        } else {
            params.put(APIUtils.TEXT_KEY, commentMarkdown);
        }
        params.put(APIUtils.THING_ID_KEY, thingFullname);

        newAuthenticatorOauthRetrofit.create(RedditAPI.class).sendCommentOrReplyToMessage(headers, params).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    ParseComment.parseSentComment(executor, handler, response.body(), parentDepth, new ParseComment.ParseSentCommentListener() {
                        @Override
                        public void onParseSentCommentSuccess(Comment comment) {
                            sendCommentListener.sendCommentSuccess(comment);
                        }

                        @Override
                        public void onParseSentCommentFailed(@Nullable String errorMessage) {
                            sendCommentListener.sendCommentFailed(errorMessage);
                        }
                    });
                } else {
                    sendCommentListener.sendCommentFailed(response.message());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                sendCommentListener.sendCommentFailed(t.getMessage());
            }
        });
    }

    public interface SendCommentListener {
        void sendCommentSuccess(Comment comment);

        void sendCommentFailed(String errorMessage);
    }
}
