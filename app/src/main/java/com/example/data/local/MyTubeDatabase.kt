package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [VideoEntity::class, CommentEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MyTubeDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun commentDao(): CommentDao
    abstract fun channelDao(): ChannelDao

    companion object {
        const val DATABASE_NAME = "mytube_offline.db"

        @Volatile
        private var INSTANCE: MyTubeDatabase? = null

        fun getInstance(context: Context): MyTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): MyTubeDatabase {
            return Room.databaseBuilder(
                appContext,
                MyTubeDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialChannels(getInstance(appContext).channelDao())
                        }
                    }
                })
                .build()
        }

        suspend fun seedInitialChannels(channelDao: ChannelDao) {
            val starterChannels = listOf(
                ChannelEntity(
                    id = "self",
                    name = "Creator",
                    handle = "@creator",
                    bio = "Your personal offline MY TUBE channel.",
                    accentColorHex = 0xFFFF2E4C,
                    isSubscribed = true,
                    isDefaultSelf = true
                ),
                ChannelEntity(
                    id = "channel_cinema_vault",
                    name = "Cinema Vault",
                    handle = "@cinemavault",
                    bio = "Local film reels, edits, and color-graded cuts.",
                    accentColorHex = 0xFF6366F1,
                    isSubscribed = true,
                    isDefaultSelf = false
                ),
                ChannelEntity(
                    id = "channel_sound_sessions",
                    name = "Sound Sessions",
                    handle = "@soundsessions",
                    bio = "Offline live sets, music videos, and studio recordings.",
                    accentColorHex = 0xFF10B981,
                    isSubscribed = false,
                    isDefaultSelf = false
                ),
                ChannelEntity(
                    id = "channel_travel_reels",
                    name = "Travel Reels",
                    handle = "@travelreels",
                    bio = "Vertical adventures and on-the-go Shorts.",
                    accentColorHex = 0xFFF59E0B,
                    isSubscribed = false,
                    isDefaultSelf = false
                )
            )
            channelDao.insertChannels(starterChannels)
        }
    }
}
