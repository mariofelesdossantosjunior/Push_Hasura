package com.mario.pushhasura

import android.app.Notification
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport
import com.mario.pushhasura.graphql.GetNotificationsSubscription
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class MainActivity : AppCompatActivity() {

    private val notifications: GetNotificationsSubscription by lazy {
        GetNotificationsSubscription.builder().build()
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(this)
    }

    private val apolloClient: ApolloClient? by lazy {
        setupApollo()
    }

    var subscription: ApolloSubscriptionCall<GetNotificationsSubscription.Data>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        subscribeNotifications(apolloClient)

    }

    override fun onResume() {
        super.onResume()
        subscribeNotifications(apolloClient)
    }

    /**
     * Configure Apollo Client
     */
    private fun setupApollo(): ApolloClient? {
        val log: HttpLoggingInterceptor =
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(log)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder().method(original.method, original.body)
                //builder.header("Authorization", authHeader)
                chain.proceed(builder.build())
            }
            .build()

        return ApolloClient
            .builder()
            .serverUrl(GRAPHQL_ENDPOINT)
            .subscriptionTransportFactory(
                WebSocketSubscriptionTransport.Factory(
                    GRAPHQL_WEBSOCKET_ENDPOINT,
                    okHttpClient
                )
            )
            .build()
    }

    /**
     * Assign Subscribe Notifications
     */
    private fun subscribeNotifications(apolloClient: ApolloClient?) {
        subscription = apolloClient?.subscribe(notifications)

        subscription?.let {
            it.execute(object : ApolloSubscriptionCall.Callback<GetNotificationsSubscription.Data> {
                override fun onFailure(e: ApolloException) {
                    Log.d(TAG, "onFailure: ${e.message}")
                }

                override fun onResponse(response: Response<GetNotificationsSubscription.Data>) {
                    Log.d(TAG, "onResponse: ${response.data().toString()}")

                    runOnUiThread {
                        showNotification(response.data())
                    }
                }

                override fun onConnected() {
                    Log.d(TAG, "onConnected")
                }

                override fun onTerminated() {
                    Log.d(TAG, "onTerminated")
                }

                override fun onCompleted() {
                    Log.d(TAG, "onCompleted")
                }

            })
        }
    }

    /**
     * Create Notification
     */
    private fun showNotification(data: GetNotificationsSubscription.Data?) {
        val mesage = makeNotification(data?.notification())

        val newMessageNotification = Notification.Builder(this)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.title))
            .setContentText(mesage)
            .build()

        notificationManager.notify(1, newMessageNotification)

    }

    /**
     * recovery last notification
     */
    private fun makeNotification(notifications: MutableList<GetNotificationsSubscription.Notification>?): String {
        var msg = ""

        notifications?.let {
            if (notifications.isNotEmpty()) {
                msg = notifications.last().message()
            }
        }
        return msg
    }

    override fun onPause() {
        super.onPause()
        subscription?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        subscription?.cancel()
        apolloClient?.disableSubscriptions()
    }

    companion object {
        private const val GRAPHQL_ENDPOINT = "https://mini-curso.herokuapp.com/v1/graphql"
        private const val GRAPHQL_WEBSOCKET_ENDPOINT = "wss://mini-curso.herokuapp.com/v1/graphql"
        val TAG = "Apollo"
    }
}
