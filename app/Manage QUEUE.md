You should clear the old queue and replace it with the new context. This is the most intuitive and common approach, used by services like Spotify. It assumes the user's listening intent has changed.

Here’s a breakdown of the two standard models for handling this.

## 1. The "Replace" Model (Recommended)

This is the simplest and most predictable user experience for a main "Play" button. The logic assumes that when a user actively chooses a new song from a different album, artist, or playlist, they want to continue listening within that new context.

How it Works:

    User Action: A user is listening to "Album A" and their queue contains the rest of that album's songs. They then browse to "Album B" and press play on a track.

    Clear Queue: Your app immediately clears the current queue (the remaining songs from Album A).

    Play New Song: The selected track from Album B starts playing instantly.

    Repopulate Queue: The app automatically fills the queue with the rest of the songs from Album B.

Why it works: It's clean and logical. The user's action directly creates a new listening session, preventing a jarring mix of genres or moods from the old and new contexts.

## 2. The "Insert" Model (Advanced Feature)

This model prioritizes preserving the existing queue. It's less common for a primary play button but is perfect for secondary actions like "Play Next" or "Add to Queue." Apple Music leans more towards this model.

How it Works:

    User Action: Same scenario, a user is listening to "Album A." They find a track on "Album B" they want to hear.

    Insert Song: Instead of clearing the queue, the app inserts the selected song from Album B at the top of the queue (to play next) or at the bottom (add to end).

    Resume Old Queue: After the inserted song finishes, the player resumes playing the original queue from Album A.

Why it's different: This is a non-destructive action. It's great for when a user wants to temporarily interrupt their current playlist for a specific song without losing their place.

## Recommendation for Your App

    For the main Play button on any song, use the "Replace" Model. It's the standard expectation.

    Implement the "Insert" Model as separate, explicit actions. You can add these via:

        A "three dots" menu (⋮) next to each song with options for "Play Next" and "Add to Queue".

        A separate plus icon (+) that adds a song to the end of the queue.